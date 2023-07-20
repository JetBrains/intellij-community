// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.icons.CachedImageIcon;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Stores the highlighting markup for the last opened files on disk on project close and restores it back to the editor on file open,
 * to reduce the "opened editor-to-some highlighting shown" perceived interval.
 */
@Service(Service.Level.PROJECT)
@State(name = "HighlightingMarkupGrave", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
final class HighlightingMarkupGrave implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(HighlightingMarkupGrave.class);
  private static final Key<Boolean> IS_ZOMBIE = Key.create("IS_ZOMBIE");
  @NotNull private final Project myProject;
  private ConcurrentMap<VirtualFile, FileMarkupInfo> cachedMarkup = new ConcurrentHashMap<>();
  HighlightingMarkupGrave(@NotNull Project project) {
    myProject = project;
    project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if (!isEnabled()) return;
        for (FileEditor fileEditor : FileEditorManager.getInstance(project).getEditors(file)) {
          if (fileEditor instanceof TextEditor textEditor) {
            resurrectZombies(textEditor.getEditor(), file);
          }
        }
      }
    });
    // as soon as highlighting kicks in and displays its own range highlighters, remove ones we applied from the on-disk cache,
    // but only after the highlighting finished, to avoid flicker
    project.getMessageBus().connect().subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
      @Override
      public void daemonFinished(@NotNull Collection<? extends @NotNull FileEditor> fileEditors) {
        if (!DumbService.getInstance(myProject).isDumb()) {
          for (FileEditor fileEditor : fileEditors) {
            if (isHighlightingCompleted(fileEditor, project)) {
              putDownActiveZombiesInFile(fileEditor);
            }
          }
        }
      }
    });
  }

  private void putDownActiveZombiesInFile(@NotNull FileEditor fileEditor) {
    if (!cachedMarkup.containsKey(fileEditor.getFile())) {
      return;
    }
    List<RangeHighlighter> toRemove = new ArrayList<>();
    if (fileEditor instanceof TextEditor textEditor) {
      MarkupModel markupModel = DocumentMarkupModel.forDocument(textEditor.getEditor().getDocument(), myProject, false);
      if (markupModel != null) {
        for (RangeHighlighter highlighter : markupModel.getAllHighlighters()) {
          if (isZombieMarkup(highlighter)) {
            toRemove.add(highlighter);
          }
        }
      }
      cachedMarkup.remove(fileEditor.getFile());
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("removing " + toRemove.size() + " markups for " + fileEditor + "; dumb=" + DumbService.getInstance(myProject).isDumb());
    }

    for (RangeHighlighter highlighter : toRemove) {
      highlighter.dispose();
    }
  }


  private void resurrectZombies(@NotNull Editor editor, @NotNull VirtualFile file) {
    FileMarkupInfo markupInfo = cachedMarkup.get(file);
    if (markupInfo == null) return;
    Document document = editor.getDocument();
    if (document.getText().hashCode() != markupInfo.contentHash()) {
      // text changed since the cached markup was saved on-disk
      if (LOG.isDebugEnabled()) {
        LOG.debug("restore canceled hash mismatch " + markupInfo.highlighters().size() + " for " + file);
      }
      return;
    }
    MarkupModel markupModel = DocumentMarkupModel.forDocument(document, myProject, true);
    for (HighlighterState state : markupInfo.highlighters()) {
      if (state.end() > document.getTextLength()) {
        // something's wrong, the document has changed in the other thread?
        continue;
      }
      RangeHighlighter highlighter;
      TextAttributesKey attributesKey = state.textAttributesKey();
      if (attributesKey == null) {
        highlighter = markupModel.addRangeHighlighter(state.start(), state.end(), state.layer(), state.textAttributes(), state.target());
      }
      else {
        highlighter = markupModel.addRangeHighlighter(attributesKey, state.start(), state.end(), state.layer(), state.target());
      }
      if (state.gutterIcon() != null) {
        highlighter.setGutterIconRenderer(new GutterIconRenderer() {
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
        });
      }
      markZombieMarkup(highlighter);
      if (LOG.isDebugEnabled()) {
        LOG.debug("create " + highlighter + "; key=" + highlighter.getTextAttributesKey() +
                 "; attr=" + highlighter.getTextAttributes(null) + "; icon=" + state.gutterIcon());
      }
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("restored " + markupInfo.highlighters().size() + " for " + file);
    }
  }

  private record FileMarkupInfo(@NotNull VirtualFile virtualFile, int contentHash, @NotNull List<HighlighterState> highlighters) {
    private FileMarkupInfo(@NotNull Project project, @NotNull VirtualFile virtualFile, @NotNull Document document, @NotNull EditorColorsScheme colorsScheme) {
      this(virtualFile, document.getText().hashCode(), HighlighterState.allHighlightersFromMarkup(project, document, colorsScheme));
    }

    static FileMarkupInfo exhume(@NotNull Element element) {
      String url = element.getAttributeValue("url", "");
      VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl(url);
      if (virtualFile == null) {
        return null;
      }
      int contentHash = Integer.parseInt(element.getAttributeValue("contentHash", ""));
      List<HighlighterState> highlighters = ContainerUtil.map(element.getChildren("highlighter"), e -> HighlighterState.exhume(e));
      return new FileMarkupInfo(virtualFile, contentHash, highlighters);
    }
    @NotNull
    Element bury() {
      Element element = new Element("markupCoffin");
      element.setAttribute("contentHash", Integer.toString(contentHash()));
      element.setAttribute("url", virtualFile().getUrl());
      element.addContent(ContainerUtil.map(highlighters(), h -> h.bury()));
      return element;
    }
  }
  private record HighlighterState(int start, int end, int layer, @NotNull HighlighterTargetArea target,
                                  @Nullable TextAttributesKey textAttributesKey,
                                  @Nullable TextAttributes textAttributes,
                                  @Nullable Icon gutterIcon) {
    @NotNull
    private static HighlighterState exhume(@NotNull Element state) {
      TextAttributesKey key;
      if (JDOMExternalizerUtil.readField(state, "myExternalName") == null) {
        key = null;
      }
      else {
        key = new TextAttributesKey(state);
      }
      TextAttributes attributes = new TextAttributes(state);
      if (attributes.isEmpty()) attributes = null;
      int start = Integer.parseInt(state.getAttributeValue("start", ""));
      int end = Integer.parseInt(state.getAttributeValue("end", ""));
      int layer = Integer.parseInt(state.getAttributeValue("layer", ""));
      HighlighterTargetArea target = HighlighterTargetArea.values()[Integer.parseInt(state.getAttributeValue("target", ""))];
      String gutterIconUrl = state.getAttributeValue("gutterIconUrl", "");
      Icon icon = null;
      if (!StringUtil.isEmpty(gutterIconUrl)) {
        try {
          URL url = new URL(gutterIconUrl);
          icon = IconLoader.findIcon(url);
        }
        catch (MalformedURLException ignored) {
        }
      }

      return new HighlighterState(start, end, layer, target, key, attributes, icon);
    }

    @NotNull
    private Element bury() {
      TextAttributesKey key = textAttributesKey();
      TextAttributes attributes = textAttributes();
      int start = start();
      int end = end();
      int layer = layer();
      HighlighterTargetArea target = target();
      Element element = new Element("highlighter");
      if (key != null) {
        key.writeExternal(element);
      }
      else if (attributes != null) {
        attributes.writeExternal(element);
      }
      element.setAttribute("start", Integer.toString(start));
      element.setAttribute("end", Integer.toString(end));
      element.setAttribute("layer", Integer.toString(layer));
      element.setAttribute("target", Integer.toString(target.ordinal()));
      Icon icon = gutterIcon();
      if (icon instanceof CachedImageIcon cii) {
        URL url = cii.getUrl();
        if (url != null) {
          element.setAttribute("gutterIconUrl", url.toExternalForm());
        }
      }

      return element;
    }

    @NotNull
    private static List<HighlighterState> allHighlightersFromMarkup(@NotNull Project project,
                                                                    @NotNull Document document, @NotNull EditorColorsScheme colorsScheme) {
      MarkupModel markupModel = DocumentMarkupModel.forDocument(document, project, false);
      if (markupModel == null) {
        return Collections.emptyList();
      }
      // for stable XML
      Comparator<HighlighterState> comparator = (h1, h2) -> h1.equals(h2) ? 0 : h1.start() != h2.start() ? Integer.compare(h1.start(), h2.start()) : h1.end() != h2.end() ? Integer.compare(h1.end(), h2.end()) : Integer.compare(h1.hashCode(), h2.hashCode());

      return Arrays.stream(markupModel.getAllHighlighters())
        .filter(h -> {
          LineMarkerInfo<?> lm;
          return h.getErrorStripeTooltip() instanceof HighlightInfo info && info.getSeverity().compareTo(HighlightSeverity.INFORMATION) > 0
                 || (lm = LineMarkersUtil.getLineMarkerInfo(h)) != null && lm.createGutterRenderer() != null;
          }
        )
        .map(h -> toState(h, colorsScheme))
        .sorted(comparator)
        .toList();
    }

    @NotNull
    private static HighlighterState toState(@NotNull RangeHighlighter highlighter, @Nullable EditorColorsScheme scheme) {
      TextAttributesKey key = highlighter.getTextAttributesKey();
      TextAttributes attributes = highlighter.getTextAttributes(scheme);
      int start = highlighter.getStartOffset();
      int end = highlighter.getEndOffset();
      int layer = highlighter.getLayer();
      HighlighterTargetArea target = highlighter.getTargetArea();
      GutterIconRenderer gutterIconRenderer = highlighter.getGutterIconRenderer();
      Icon icon = gutterIconRenderer == null ? null : gutterIconRenderer.getIcon();
      return new HighlighterState(start, end, layer, target, key, attributes, icon);
    }
  }

  @Override
  public void loadState(@NotNull Element state) {
    if (!isEnabled()) return;
    cachedMarkup =
    state.getChildren("markupCoffin")
      .stream()
      .map(markupElement -> FileMarkupInfo.exhume(markupElement))
      .filter(Objects::nonNull)
      .collect(Collectors.toConcurrentMap(m->m.virtualFile(), m->m));
    if (LOG.isDebugEnabled()) {
      LOG.debug("loaded markup for " + StringUtil.join(cachedMarkup.keySet(), v -> v.getName(), ","));
    }
  }

  @Override
  public Element getState() {
    if (!isEnabled()) return null;
    Map<VirtualFile, FileMarkupInfo> markup =
    Arrays.stream(FileEditorManager.getInstance(myProject).getAllEditors())
      .filter(fe -> fe instanceof TextEditor)
      .filter(fe -> fe.getFile() != null)
      .map(fe -> Trinity.create(fe.getFile(), ((TextEditor)fe).getEditor().getDocument(), ((TextEditor)fe).getEditor().getColorsScheme()))
      .distinct()
      .map(t -> new FileMarkupInfo(myProject, t.getFirst(), t.getSecond(), t.getThird()))
      .filter(m->!m.highlighters().isEmpty())
      .collect(Collectors.toMap(m->m.virtualFile(), m->m));

    List<Element> markupElements = markup.entrySet().stream()
      .sorted(Comparator.comparing(e -> e.getKey().getUrl()))
      .map(e -> e.getValue().bury())
      .toList();
    Element element = new Element("root");
    element.addContent(markupElements);
    if (LOG.isDebugEnabled()) {
      LOG.debug("saved markup for " + StringUtil.join(markup.keySet(), v -> v.getName(), ","));
    }
    return element;
  }

  static boolean isEnabled() {
    return Registry.is("cache.higlighting.markup.on.disk");
  }

  private static boolean isHighlightingCompleted(@NotNull FileEditor fileEditor, @NotNull Project project) {
    return fileEditor instanceof TextEditor textEditor
           && DaemonCodeAnalyzerEx.getInstanceEx(project).getFileStatusMap().allDirtyScopesAreNull(textEditor.getEditor().getDocument());
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

}
