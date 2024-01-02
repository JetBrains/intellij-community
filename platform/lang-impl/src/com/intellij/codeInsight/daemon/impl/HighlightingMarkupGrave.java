// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.featureStatistics.fusCollectors.FileEditorCollector;
import com.intellij.featureStatistics.fusCollectors.FileEditorCollector.MarkupGraveEvent;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.text.TextEditorCache;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.io.IOUtil;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

import static com.intellij.util.io.DataInputOutputUtil.readINT;
import static com.intellij.util.io.DataInputOutputUtil.writeINT;

/**
 * Stores the highlighting markup on disk on file close and restores it back to the editor on file open,
 * to reduce the "opened editor-to-some highlighting shown" perceived interval.
 */
@Service(Service.Level.PROJECT)
final class HighlightingMarkupGrave {
  private static final Logger LOG = Logger.getInstance(HighlightingMarkupGrave.class);
  private static final Key<Boolean> IS_ZOMBIE = Key.create("IS_ZOMBIE");

  private final @NotNull Project myProject;
  private final @NotNull ConcurrentIntObjectMap<Boolean> myResurrectedZombies; // fileId -> isMarkupModelPreferable
  private final @NotNull HighlightingMarkupStore myMarkupStore;

  HighlightingMarkupGrave(@NotNull Project project, @NotNull CoroutineScope scope) {
    // check that important TextAttributesKeys are initialized
    assert DefaultLanguageHighlighterColors.INSTANCE_FIELD.getFallbackAttributeKey() != null : DefaultLanguageHighlighterColors.INSTANCE_FIELD;

    myProject = project;
    myResurrectedZombies = ConcurrentCollectionFactory.createConcurrentIntObjectMap();
    myMarkupStore = new HighlightingMarkupStore(project, scope);

    subscribeDaemonFinished();
    subscribeFileClosed();
  }

  private void subscribeDaemonFinished() {
    // as soon as highlighting kicks in and displays its own range highlighters, remove ones we applied from the on-disk cache,
    // but only after the highlighting finished, to avoid flicker
    myProject.getMessageBus().connect().subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
      @Override
      public void daemonFinished(@NotNull Collection<? extends @NotNull FileEditor> fileEditors) {
        if (!DumbService.getInstance(myProject).isDumb()) {
          for (FileEditor fileEditor : fileEditors) {
            if (fileEditor instanceof TextEditor textEditor &&
                textEditor.getEditor().getEditorKind() == EditorKind.MAIN_EDITOR &&
                DaemonCodeAnalyzerEx.isHighlightingCompleted(textEditor, myProject)) {
              putDownActiveZombiesInFile(textEditor);
            }
          }
        }
      }
    });
  }

  private void subscribeFileClosed() {
    myProject.getMessageBus().connect().subscribe(
      FileEditorManagerListener.Before.FILE_EDITOR_MANAGER,
      new FileEditorManagerListener.Before() {
        @Override
        public void beforeFileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
          putInGrave(source, file);
        }
      }
    );
  }

  private void putDownActiveZombiesInFile(@NotNull TextEditor textEditor) {
    if (!(textEditor.getFile() instanceof VirtualFileWithId fileWithId)) {
      return;
    }
    boolean replaced = myResurrectedZombies.replace(fileWithId.getId(), false, true);
    if (!replaced) {
      // no zombie or zombie already disposed
      return;
    }
    List<RangeHighlighter> toRemove = null;
    MarkupModel markupModel = DocumentMarkupModel.forDocument(textEditor.getEditor().getDocument(), myProject, false);
    if (markupModel != null) {
      for (RangeHighlighter highlighter : markupModel.getAllHighlighters()) {
        if (isZombieMarkup(highlighter)) {
          if (toRemove == null) {
            toRemove = new ArrayList<>();
          }
          toRemove.add(highlighter);
        }
      }
    }
    if (toRemove == null) {
      return;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("removing " + toRemove.size() + " markups for " + textEditor + "; dumb=" + DumbService.getInstance(myProject).isDumb());
    }

    for (RangeHighlighter highlighter : toRemove) {
      highlighter.dispose();
    }
  }

  void resurrectZombies(@NotNull Document document, @NotNull VirtualFileWithId file) {
    if (myResurrectedZombies.containsKey(file.getId())) {
      return;
    }
    FileMarkupInfo markupInfo = myMarkupStore.getMarkup(file);
    if (markupInfo == null) {
      myResurrectedZombies.put(file.getId(), true);
      logFusStatistic(file, MarkupGraveEvent.NOT_RESTORED_CACHE_MISS);
      return;
    }

    if (contentHash(document) != markupInfo.contentHash()) {
      // text changed since the cached markup was saved on-disk
      if (LOG.isDebugEnabled()) {
        LOG.debug("restore canceled hash mismatch " + markupInfo.size() + " for " + file);
      }
      myMarkupStore.removeMarkup(file);
      myResurrectedZombies.put(file.getId(), true);
      logFusStatistic(file, MarkupGraveEvent.NOT_RESTORED_CONTENT_CHANGED);
      return;
    }

    MarkupModel markupModel = DocumentMarkupModel.forDocument(document, myProject, true);
    for (HighlighterState state : markupInfo.highlighters()) {
      int textLength = document.getTextLength();
      if (state.end() > textLength) {
        // something's wrong, the document has changed in the other thread?
        LOG.warn("skipped " + state + " as it is out of document with length " + textLength);
        continue;
      }
      RangeHighlighter highlighter;
      // re-read TextAttributesKey because it might be read too soon, with its myFallbackKey uninitialized.
      // (still store TextAttributesKey by instance, instead of String, to intern its external name)
      TextAttributesKey key = state.textAttributesKey() == null ? null : TextAttributesKey.find(state.textAttributesKey().getExternalName());
      if (key == null) {
        highlighter = markupModel.addRangeHighlighter(state.start(), state.end(), state.layer(), state.textAttributes(), state.targetArea());
      }
      else {
        highlighter = markupModel.addRangeHighlighter(key, state.start(), state.end(), state.layer(), state.targetArea());
      }
      if (state.gutterIcon() != null) {
        GutterIconRenderer fakeIcon = new GutterIconRenderer() {
          @Override
          public boolean equals(Object obj) {
            return false;
          }

          @Override
          public int hashCode() {
            return 0;
          }

          @Override
          public @NotNull Icon getIcon() {
            return state.gutterIcon();
          }
        };
        highlighter.setGutterIconRenderer(fakeIcon);
      }
      markZombieMarkup(highlighter);
    }
    logFusStatistic(file, MarkupGraveEvent.RESTORED, markupInfo.size());
    FUSProjectHotStartUpMeasurer.INSTANCE.markupRestored(file);
    if (LOG.isDebugEnabled()) {
      LOG.debug("restored " + markupInfo.size() + " for " + file);
    }
    myResurrectedZombies.put(file.getId(), false);
  }

  private void putInGrave(@NotNull FileEditorManager editorManager, @NotNull VirtualFile file) {
    if (!(file instanceof VirtualFileWithId fileWithId)) {
      return;
    }
    FileEditor fileEditor = editorManager.getSelectedEditor(file);
    if (!(fileEditor instanceof TextEditor textEditor)) {
      return;
    }
    if (textEditor.getEditor().getEditorKind() != EditorKind.MAIN_EDITOR) {
      return;
    }
    Document document = FileDocumentManager.getInstance().getCachedDocument(file);
    if (document == null) {
      return;
    }
    EditorColorsScheme colorsScheme = textEditor.getEditor().getColorsScheme();
    myMarkupStore.executeAsync(() -> {
      ReadAction.run(() -> {
        FileMarkupInfo markupFromModel = getMarkupFromModel(document, colorsScheme);
        FileMarkupInfo storedMarkup = myMarkupStore.getMarkup(fileWithId);
        Boolean zombieDisposed = myResurrectedZombies.get(fileWithId.getId());
        GraveDecision graveDecision = GraveDecision.getDecision(markupFromModel, storedMarkup, zombieDisposed);
        switch (graveDecision) {
          case STORE_NEW -> {
            myMarkupStore.putMarkup(fileWithId, markupFromModel);
            if (LOG.isDebugEnabled()) {
              LOG.debug("stored markup " + markupFromModel.size() + " for " + file);
            }
          }
          case REMOVE_OLD -> {
            myMarkupStore.removeMarkup(fileWithId);
            if (LOG.isDebugEnabled() && storedMarkup != null) {
              LOG.debug("removed outdated markup " + storedMarkup.size() + " for " + file);
            }
          }
          case KEEP_OLD -> {
            if (LOG.isDebugEnabled() && storedMarkup != null) {
              LOG.debug("preserved markup " + storedMarkup.size() + " for " + file);
            }
          }
        }
      });
    });
  }

  private @NotNull FileMarkupInfo getMarkupFromModel(@NotNull Document document, @NotNull EditorColorsScheme colorsScheme) {
    return new FileMarkupInfo(
      contentHash(document),
      HighlighterState.allHighlightersFromMarkup(myProject, document, colorsScheme)
    );
  }

  record FileMarkupInfo(int contentHash, @NotNull List<@NotNull HighlighterState> highlighters) {
    static @NotNull FileMarkupInfo exhume(@NotNull DataInput in) throws IOException {
      int contentHash = readINT(in);
      int hCount = readINT(in);
      ArrayList<HighlighterState> highlighters = new ArrayList<>(hCount);
      for (int i = 0; i < hCount; i++) {
        HighlighterState highlighterState = HighlighterState.exhume(in);
        highlighters.add(highlighterState);
      }
      return new FileMarkupInfo(contentHash, Collections.unmodifiableList(highlighters));
    }

    void bury(@NotNull DataOutput out) throws IOException {
      writeINT(out, contentHash);
      writeINT(out, highlighters.size());
      for (HighlighterState highlighterState : highlighters) {
        highlighterState.bury(out);
      }
    }

    boolean isEmpty() {
      return highlighters.isEmpty();
    }

    int size() {
      return highlighters.size();
    }
  }

  record HighlighterState(
    int start,
    int end,
    int layer,
    @NotNull HighlighterTargetArea targetArea,
    @Nullable TextAttributesKey textAttributesKey,
    @Nullable TextAttributes textAttributes,
    @Nullable Icon gutterIcon
  ) {
    private HighlighterState(@NotNull RangeHighlighter highlighter, @NotNull EditorColorsScheme colorsScheme) {
      this(
        highlighter.getStartOffset(),
        highlighter.getEndOffset(),
        highlighter.getLayer(),
        highlighter.getTargetArea(),
        highlighter.getTextAttributesKey(),
        highlighter.getTextAttributes(colorsScheme),
        highlighter.getGutterIconRenderer() == null ? null : highlighter.getGutterIconRenderer().getIcon()
      );
    }

    static @NotNull HighlighterState exhume(@NotNull DataInput in) throws IOException {
      int start = readINT(in);
      int end = readINT(in);
      int layer = readINT(in);
      int target = readINT(in);
      TextAttributesKey key = in.readBoolean() ? TextAttributesKey.find(IOUtil.readUTF(in)) : null;
      TextAttributes attributes = in.readBoolean() ? new TextAttributes(in) : null;
      Icon icon = EditorCacheKt.readGutterIcon(in);
      return new HighlighterState(start, end, layer, HighlighterTargetArea.values()[target], key, attributes, icon);
    }

    void bury(@NotNull DataOutput out) throws IOException {
      writeINT(out, start);
      writeINT(out, end);
      writeINT(out, layer);
      writeINT(out, targetArea.ordinal());
      writeTextAttributesKey(out);
      writeTextAttributes(out);
      EditorCacheKt.writeGutterIcon(gutterIcon, out);
    }

    private void writeTextAttributesKey(@NotNull DataOutput out) throws IOException {
      boolean attributesKeyExists = textAttributesKey != null;
      out.writeBoolean(attributesKeyExists);
      if (attributesKeyExists) {
        IOUtil.writeUTF(out, textAttributesKey.getExternalName());
      }
    }

    private void writeTextAttributes(@NotNull DataOutput out) throws IOException {
      boolean attributesExists = textAttributes != null && textAttributesKey == null;
      out.writeBoolean(attributesExists);
      if (attributesExists) {
        textAttributes.writeExternal(out);
      }
    }

    @Override
    public boolean equals(Object o) {
      // exclude gutterIcon
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      HighlighterState state = (HighlighterState)o;
      return start == state.start &&
             end == state.end &&
             layer == state.layer &&
             targetArea == state.targetArea &&
             Objects.equals(textAttributesKey, state.textAttributesKey) &&
             Objects.equals(textAttributes, state.textAttributes);
    }

    @Override
    public int hashCode() {
      // exclude gutterIcon
      return Objects.hash(start, end, layer, targetArea, textAttributesKey, textAttributes);
    }

    @NotNull
    private static List<HighlighterState> allHighlightersFromMarkup(@NotNull Project project,
                                                                    @NotNull Document document,
                                                                    @NotNull EditorColorsScheme colorsScheme) {
      MarkupModel markupModel = DocumentMarkupModel.forDocument(document, project, false);
      if (markupModel == null) {
        return Collections.emptyList();
      }
      // for stable XML
      Comparator<HighlighterState> comparator = (h1, h2) -> h1.equals(h2) ? 0 : h1.start() != h2.start() ? Integer.compare(h1.start(), h2.start()) : h1.end() != h2.end() ? Integer.compare(h1.end(), h2.end()) : Integer.compare(h1.hashCode(), h2.hashCode());

      return Arrays.stream(markupModel.getAllHighlighters())
        .filter(h -> {
          LineMarkerInfo<?> lm;
          HighlightInfo info = HighlightInfo.fromRangeHighlighter(h);
          return info != null &&
                 (info.getSeverity().compareTo(HighlightSeverity.INFORMATION) > 0   // either warning/error or symbol type (e.g. field text attribute)
                  || info.getSeverity() == HighlightInfoType.SYMBOL_TYPE_SEVERITY
                 )
                 || (lm = LineMarkersUtil.getLineMarkerInfo(h)) != null && lm.getIcon() != null; // or a line marker with a gutter icon
          }
        )
        .map(h -> new HighlighterState(h, colorsScheme))
        .sorted(comparator)
        .toList();
    }
  }

  private enum GraveDecision {
    STORE_NEW,
    KEEP_OLD,
    REMOVE_OLD;

    static GraveDecision getDecision(
      @NotNull FileMarkupInfo newMarkup,
      @Nullable FileMarkupInfo oldMarkup,
      @Nullable Boolean isNewMoreRelevant
    ) {
      if (oldMarkup == null && !newMarkup.isEmpty()) {
        // put zombie's limbs
        return STORE_NEW;
      }
      if (oldMarkup == null) {
        // no a limb to put in grave
        return KEEP_OLD;
      }
      if (oldMarkup.contentHash() != newMarkup.contentHash() && !newMarkup.isEmpty()) {
        // fresh limbs
        return STORE_NEW;
      }
      if (oldMarkup.contentHash() != newMarkup.contentHash()) {
        // graved zombie is rotten and there is no a limb to bury
        return REMOVE_OLD;
      }
      if (newMarkup.isEmpty()) {
        // graved zombie is still fresh
        return KEEP_OLD;
      }
      if (isNewMoreRelevant == null) {
        // should never happen. file is closed without being opened before
        return STORE_NEW;
      }
      if (isNewMoreRelevant) {
        // limbs form complete zombie
        return STORE_NEW;
      }
      return KEEP_OLD;
    }
  }

  static boolean isEnabled() {
    return Registry.is("cache.highlighting.markup.on.disk", true);
  }

  @TestOnly
  static void runInEnabled(@NotNull Runnable runnable) {
    boolean wasEnabled = isEnabled();
    Registry.get("cache.highlighting.markup.on.disk").setValue(true);
    try {
      runnable.run();
    }
    finally {
      Registry.get("cache.highlighting.markup.on.disk").setValue(wasEnabled);
    }
  }

  static boolean isZombieMarkup(@NotNull RangeMarker highlighter) {
    return highlighter.getUserData(IS_ZOMBIE) != null;
  }

  private static void markZombieMarkup(@NotNull RangeMarker highlighter) {
    highlighter.putUserData(IS_ZOMBIE, Boolean.TRUE);
  }

  static void unmarkZombieMarkup(@NotNull RangeMarker highlighter) {
    highlighter.putUserData(IS_ZOMBIE, null);
  }

  @TestOnly
  void clearResurrectedZombies() {
    myResurrectedZombies.clear();
  }

  private static int contentHash(@NotNull Document document) {
    return TextEditorCache.Companion.contentHash(document);
  }

  private void logFusStatistic(@NotNull VirtualFileWithId file, @NotNull MarkupGraveEvent event) {
    logFusStatistic(file, event, 0);
  }

  private void logFusStatistic(@NotNull VirtualFileWithId file, @NotNull MarkupGraveEvent event, int restoredCount) {
    FileEditorCollector.logEditorMarkupGrave(myProject, (VirtualFile) file, event, restoredCount);
  }
}
