/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.impl.EditorHyperlinkSupport;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutterAction;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
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
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.actions.ActiveAnnotationGutter;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.annotate.AnnotationSource;
import com.intellij.openapi.vcs.annotate.ShowAllAffectedGenericAction;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
* @author Konstantin Bulenkov
*/
class AnnotateStackTraceAction extends AnAction implements DumbAware {
  private final EditorHyperlinkSupport myHyperlinks;
  private Map<Integer, VcsFileRevision> cache;
  private int newestLine = -1;
  private int maxDateLength = 0;
  private final Editor myEditor;
  private boolean myGutterShowed = false;
  private final HashMap<VirtualFile, List<Integer>> files2lines = new HashMap<VirtualFile, List<Integer>>();

  AnnotateStackTraceAction(@NotNull ConsoleViewImpl consoleView) {
    super("Show files modification info", null, AllIcons.Actions.Annotate);
    myHyperlinks = consoleView.getHyperlinks();
    myEditor = consoleView.getEditor();
    myEditor.getColorsScheme().setColor(
      EditorColors.CARET_ROW_COLOR, EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.CARET_ROW_COLOR));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    cache = new HashMap<Integer, VcsFileRevision>();

    ProgressManager.getInstance().run(
    new Task.Backgroundable(myEditor.getProject(), "Getting file history", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
      @Override
      public boolean shouldStartInBackground() {
        return true;
      }

      @Override
      public void onSuccess() {
      }

      private void showGutter() {
        final EditorGutterAction action = new EditorGutterAction() {
          @Override
          public void doAction(int lineNum) {
            final VcsFileRevision revision = cache.get(lineNum);
            final List<RangeHighlighter> links = myHyperlinks.findAllHyperlinksOnLine(lineNum);
            if (!links.isEmpty()) {
              HyperlinkInfo info = myHyperlinks.getHyperlinks().get(links.get(links.size() - 1));

              if (info instanceof FileHyperlinkInfo) {
                final VirtualFile file = ((FileHyperlinkInfo)info).getDescriptor().getFile();
                final Project project = getProject();
                final AbstractVcs vcs = ProjectLevelVcsManagerEx.getInstanceEx(project).getVcsFor(file);
                if (vcs != null) {
                  final VcsRevisionNumber number = revision.getRevisionNumber();
                  final VcsKey vcsKey = vcs.getKeyInstanceMethod();
                  ShowAllAffectedGenericAction.showSubmittedFiles(project, number, file, vcsKey);
                }
              }
            }
          }

          @Override
          public Cursor getCursor(int lineNum) {
            return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
          }
        };

        myEditor.getGutter().registerTextAnnotation(new ActiveAnnotationGutter() {
          @Override
          public void doAction(int lineNum) {
          }

          @Override
          public Cursor getCursor(int lineNum) {
            return Cursor.getDefaultCursor();
          }

          @Override
          public String getLineText(int line, Editor editor) {
            final VcsFileRevision revision = cache.get(line);
            if (revision != null) {
              return String.format("%"+maxDateLength+"s", DateFormatUtil.formatPrettyDate(revision.getRevisionDate())) + " " + revision.getAuthor();
            }
            return "";
          }

          @Override
          public String getToolTip(int line, Editor editor) {
            final VcsFileRevision revision = cache.get(line);
            if (revision != null) {
              return XmlStringUtil.wrapInHtml(
                revision.getAuthor() +
                " " +
                DateFormatUtil.formatDateTime(revision.getRevisionDate()) +
                "<br/>" +
                revision.getCommitMessage()
              );
            }
            return null;
          }

          @Override
          public EditorFontType getStyle(int line, Editor editor) {
            return line == newestLine ? EditorFontType.BOLD : EditorFontType.PLAIN;
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
        }, action);

        myGutterShowed = true;
      }

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        Date newestDate = null;

        List<VirtualFile> files = new ArrayList<VirtualFile>();
        for (int line = 0; line < myEditor.getDocument().getLineCount(); line++) {
          indicator.checkCanceled();
          final List<RangeHighlighter> links = myHyperlinks.findAllHyperlinksOnLine(line);
          if (links.size() > 0) {
            final HyperlinkInfo info = myHyperlinks.getHyperlinks().get(links.get(links.size() - 1));
            if (info instanceof FileHyperlinkInfo) {
              final OpenFileDescriptor fileDescriptor = ((FileHyperlinkInfo)info).getDescriptor();
              if (fileDescriptor != null) {
                final VirtualFile file = fileDescriptor.getFile();
                if (files2lines.containsKey(file)) {
                  files2lines.get(file).add(line);
                } else {
                  final ArrayList<Integer> lines = new ArrayList<Integer>();
                  lines.add(line);
                  files2lines.put(file, lines);
                  files.add(file);
                }
              }
            }
          }
        }


        for (VirtualFile file : files) {
          indicator.checkCanceled();
          final AbstractVcs vcs = VcsUtil.getVcsFor(myEditor.getProject(), file);
          FilePath filePath = VcsContextFactory.SERVICE.getInstance().createFilePathOn(file);
          if (vcs != null) {
            try {
              final VcsHistoryProvider provider = vcs.getVcsHistoryProvider();
              final VcsHistorySession session;
              if (provider != null) {
                session = provider.createSessionFor(filePath);
                final List<VcsFileRevision> list;
                if (session != null) {
                  list = session.getRevisionList();
                  final List<Integer> lines = files2lines.get(file);
                  if (list != null && !list.isEmpty()) {
                    final VcsFileRevision revision = list.get(0);
                    final Date date = revision.getRevisionDate();
                    if (newestDate == null || date.after(newestDate)) {
                      newestDate = date;
                      newestLine = lines.get(0);
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
                        } else {
                          ((EditorGutterComponentEx)myEditor.getGutter()).revalidateMarkup();
                        }
                      }
                    });
                  }
                }
              }
            }
            catch (VcsException ignored) {
              ignored.printStackTrace();
            }
          }

        }
      }
    });
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(cache == null || !myGutterShowed);
  }
}
