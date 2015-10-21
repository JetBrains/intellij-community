/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.unscramble;

import com.intellij.execution.filters.FileHyperlinkInfo;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.impl.EditorHyperlinkSupport;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.ActiveAnnotationGutter;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.annotate.AnnotationSource;
import com.intellij.openapi.vcs.annotate.ShowAllAffectedGenericAction;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcs.history.VcsHistoryProviderEx;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.CalledWithReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
* @author Konstantin Bulenkov
*/
public class AnnotateStackTraceAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(AnnotateStackTraceAction.class);

  private final EditorHyperlinkSupport myHyperlinks;
  private Map<Integer, LastRevision> cache;
  private Date newestDate;
  private int maxDateLength = 0;
  private final Editor myEditor;
  private boolean myGutterShowed = false;

  public AnnotateStackTraceAction(@NotNull Editor editor, @NotNull EditorHyperlinkSupport hyperlinks) {
    super("Show files modification info", null, AllIcons.Actions.Annotate);
    myHyperlinks = hyperlinks;
    myEditor = editor;
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    cache = new HashMap<Integer, LastRevision>();

    ProgressManager.getInstance().run(
    new Task.Backgroundable(myEditor.getProject(), "Getting File History", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
      @Override
      public boolean shouldStartInBackground() {
        return true;
      }

      @Override
      public void onSuccess() {
      }

      private void showGutter() {
        ActiveAnnotationGutter gutter = new ActiveAnnotationGutter() {
          @Override
          public void doAction(int lineNum) {
            final LastRevision revision = cache.get(lineNum);
            if (revision == null) return;

            VirtualFile file = getHyperlinkVirtualFile(myHyperlinks.findAllHyperlinksOnLine(lineNum));
            if (file == null) return;

            final Project project = getProject();
            final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
            if (vcs != null) {
              final VcsRevisionNumber number = revision.getNumber();
              final VcsKey vcsKey = vcs.getKeyInstanceMethod();
              ShowAllAffectedGenericAction.showSubmittedFiles(project, number, file, vcsKey);
            }
          }

          @Override
          public Cursor getCursor(int lineNum) {
            return cache.containsKey(lineNum) ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor();
          }

          @Override
          public String getLineText(int line, Editor editor) {
            final LastRevision revision = cache.get(line);
            if (revision != null) {
              return String.format("%"+maxDateLength+"s", DateFormatUtil.formatPrettyDate(revision.getDate())) + " " + revision.getAuthor();
            }
            return "";
          }

          @Override
          public String getToolTip(int line, Editor editor) {
            final LastRevision revision = cache.get(line);
            if (revision != null) {
              return XmlStringUtil.escapeString(
                revision.getAuthor() + " " + DateFormatUtil.formatDateTime(revision.getDate()) + "\n" +
                revision.getMessage()
              );
            }
            return null;
          }

          @Override
          public EditorFontType getStyle(int line, Editor editor) {
            LastRevision revision = cache.get(line);
            return revision != null && revision.getDate().equals(newestDate) ? EditorFontType.BOLD : EditorFontType.PLAIN;
          }

          @Override
          public ColorKey getColor(int line, Editor editor) {
            return AnnotationSource.LOCAL.getColor();
          }

          @Override
          public Color getBgColor(int line, Editor editor) {
            return null;
          }

          @Override
          public List<AnAction> getPopupActions(int line, Editor editor) {
            return Collections.emptyList();
          }

          @Override
          public void gutterClosed() {
            myGutterShowed = false;
          }
        };
        myEditor.getGutter().registerTextAnnotation(gutter, gutter);

        myGutterShowed = true;
      }

      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        final List<VirtualFile> files = new ArrayList<VirtualFile>();
        final HashMap<VirtualFile, List<Integer>> files2lines = new HashMap<VirtualFile, List<Integer>>();

        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            for (int line = 0; line < myEditor.getDocument().getLineCount(); line++) {
              indicator.checkCanceled();
              VirtualFile file = getHyperlinkVirtualFile(myHyperlinks.findAllHyperlinksOnLine(line));
              if (file == null) continue;

              if (files2lines.containsKey(file)) {
                files2lines.get(file).add(line);
              }
              else {
                final ArrayList<Integer> lines = new ArrayList<Integer>();
                lines.add(line);
                files2lines.put(file, lines);
                files.add(file);
              }
            }
          }
        });

        for (VirtualFile file : files) {
          indicator.checkCanceled();

          LastRevision revision = getLastRevision(file);
          if (revision != null) {
            final List<Integer> lines = files2lines.get(file);

            final Date date = revision.getDate();
            if (newestDate == null || date.after(newestDate)) {
              newestDate = date;
            }
            final int length = DateFormatUtil.formatPrettyDate(date).length();
            if (length > maxDateLength) {
              maxDateLength = length;
            }
            for (Integer line : lines) {
              cache.put(line, revision);
            }
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                if (!myGutterShowed) {
                  showGutter();
                }
                else {
                  ((EditorGutterComponentEx)myEditor.getGutter()).revalidateMarkup();
                }
              }
            });
          }
        }
      }

      @Nullable
      private LastRevision getLastRevision(@NotNull VirtualFile file) {
        try {
          final AbstractVcs vcs = VcsUtil.getVcsFor(myEditor.getProject(), file);
          if (vcs == null) return null;

          VcsHistoryProvider historyProvider = vcs.getVcsHistoryProvider();
          if (historyProvider == null) return null;

          FilePath filePath = VcsContextFactory.SERVICE.getInstance().createFilePathOn(file);

          if (historyProvider instanceof VcsHistoryProviderEx) {
            VcsFileRevision revision = ((VcsHistoryProviderEx)historyProvider).getLastRevision(filePath);
            if (revision == null) return null;
            return LastRevision.create(revision);
          } else {
            VcsHistorySession session = historyProvider.createSessionFor(filePath);
            if (session == null) return null;

            List<VcsFileRevision> list = session.getRevisionList();
            if (list == null || list.isEmpty()) return null;

            return LastRevision.create(list.get(0));
          }
        }
        catch (VcsException ignored) {
          LOG.warn(ignored);
          return null;
        }
      }
    });
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(!myGutterShowed);
  }

  @Nullable
  @CalledWithReadLock
  private static VirtualFile getHyperlinkVirtualFile(@NotNull List<RangeHighlighter> links) {
    RangeHighlighter key = ContainerUtil.getLastItem(links);
    if (key == null) return null;
    HyperlinkInfo info = EditorHyperlinkSupport.getHyperlinkInfo(key);
    if (!(info instanceof FileHyperlinkInfo)) return null;
    OpenFileDescriptor descriptor = ((FileHyperlinkInfo)info).getDescriptor();
    return descriptor != null ? descriptor.getFile() : null;
  }

  private static class LastRevision {
    @NotNull private final VcsRevisionNumber myNumber;
    @NotNull private final String myAuthor;
    @NotNull private final Date myDate;
    @NotNull private final String myMessage;

    public LastRevision(@NotNull VcsRevisionNumber number, @NotNull String author, @NotNull Date date, @NotNull String message) {
      myNumber = number;
      myAuthor = author;
      myDate = date;
      myMessage = message;
    }

    @NotNull
    public static LastRevision create(@NotNull VcsFileRevision revision) {
      VcsRevisionNumber number = revision.getRevisionNumber();
      String author = StringUtil.notNullize(revision.getAuthor(), "Unknown");
      Date date = revision.getRevisionDate();
      String message = StringUtil.notNullize(revision.getCommitMessage());
      return new LastRevision(number, author, date, message);
    }

    @NotNull
    public VcsRevisionNumber getNumber() {
      return myNumber;
    }

    @NotNull
    public String getAuthor() {
      return myAuthor;
    }

    @NotNull
    public Date getDate() {
      return myDate;
    }

    @NotNull
    public String getMessage() {
      return myMessage;
    }
  }
}
