/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.application.options;

import com.intellij.application.options.codeStyle.CodeStyleSchemesModel;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.ui.UserActivityListener;
import com.intellij.ui.UserActivityWatcher;
import com.intellij.util.Alarm;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;

public abstract class CodeStyleAbstractPanel implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.CodeStyleXmlPanel");
  private final Editor myEditor;
  private final CodeStyleSettings mySettings;
  private boolean myShouldUpdatePreview;
  protected static final int[] ourWrappings =
    {CodeStyleSettings.DO_NOT_WRAP, CodeStyleSettings.WRAP_AS_NEEDED, CodeStyleSettings.WRAP_ON_EVERY_ITEM, CodeStyleSettings.WRAP_ALWAYS};
  private long myLastDocumentModificationStamp;
  private String myTextToReformat = null;
  private final UserActivityWatcher myUserActivityWatcher = new UserActivityWatcher();

  private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  private CodeStyleSchemesModel myModel;
  private boolean mySomethingChanged = false;

  private synchronized void setSomethingChanged(final boolean b) {
    mySomethingChanged = b;
  }

  private synchronized boolean isSomethingChanged() {
    return mySomethingChanged;
  }

  protected CodeStyleAbstractPanel(CodeStyleSettings settings) {
    mySettings = settings;
    myEditor = createEditor();

    myUpdateAlarm.setActivationComponent(myEditor.getComponent());
    myUserActivityWatcher.addUserActivityListener(new UserActivityListener() {
      public void stateChanged() {
        somethingChanged();
      }
    });
  }

  public void setModel(final CodeStyleSchemesModel model) {
    myModel = model;
  }

  protected void somethingChanged() {
    if (myModel != null) {
      myModel.fireCurrentSettingsChanged();
    }
  }

  protected void addPanelToWatch(Component component) {
    myUserActivityWatcher.register(component);
  }

  private Editor createEditor() {
    EditorFactory editorFactory = EditorFactory.getInstance();
    myTextToReformat = getPreviewText();
    Document editorDocument = editorFactory.createDocument(myTextToReformat);
    EditorEx editor = (EditorEx)editorFactory.createViewer(editorDocument);

    myLastDocumentModificationStamp = editor.getDocument().getModificationStamp();

    EditorSettings editorSettings = editor.getSettings();
    fillEditorSettings(editorSettings);

    updatePreviewHighlighter(editor);

    return editor;
  }

  private void updatePreviewHighlighter(final EditorEx editor) {
    EditorColorsScheme scheme = editor.getColorsScheme();
    scheme.setColor(EditorColors.CARET_ROW_COLOR, null);

    editor.setHighlighter(createHighlighter(scheme));
  }

  protected void updatePreviewEditor() {
    myTextToReformat = getPreviewText();
    updatePreview();
    updatePreviewHighlighter((EditorEx)myEditor);
  }

  protected abstract EditorHighlighter createHighlighter(final EditorColorsScheme scheme);

  private void fillEditorSettings(final EditorSettings editorSettings) {
    editorSettings.setWhitespacesShown(true);
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setIndentGuidesShown(false);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setFoldingOutlineShown(false);
    editorSettings.setAdditionalColumnsCount(0);
    editorSettings.setAdditionalLinesCount(1);
    final int rightMargin = getRightMargin();
    if (rightMargin > 0) {
      editorSettings.setRightMargin(rightMargin);
    }
  }

  protected abstract int getRightMargin();

  public final void updatePreview() {
    if (!myShouldUpdatePreview || !myEditor.getComponent().isShowing()) {
      return;
    }

    if (myLastDocumentModificationStamp != myEditor.getDocument().getModificationStamp()) {
      myTextToReformat = myEditor.getDocument().getText();
    }

    int currOffs = myEditor.getScrollingModel().getVerticalScrollOffset();

    final Project finalProject = getCurrentProject();
    CommandProcessor.getInstance().executeCommand(finalProject, new Runnable() {
      public void run() {
        replaceText(finalProject);
      }
    }, null, null);
    myEditor.getSettings().setRightMargin(getRightMargin());
    myLastDocumentModificationStamp = myEditor.getDocument().getModificationStamp();
    myEditor.getScrollingModel().scrollVertically(currOffs);
  }

  private void replaceText(final Project project) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          //important not mark as generated not to get the classes before setting language level
          PsiFile psiFile = createFileFromText(project, myTextToReformat);

          prepareForReformat(psiFile);
          apply(mySettings);
          CodeStyleSettings clone = mySettings.clone();
          if (getRightMargin() > 0) {
            clone.RIGHT_MARGIN = getRightMargin();
          }


          CodeStyleSettingsManager.getInstance(project).setTemporarySettings(clone);
          PsiFile formatted = doReformat(project, psiFile);
          CodeStyleSettingsManager.getInstance(project).dropTemporarySettings();

          myEditor.getSettings().setTabSize(clone.getTabSize(getFileType()));
          Document document = myEditor.getDocument();
          document.replaceString(0, document.getTextLength(), formatted.getText());
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
  }

  protected void prepareForReformat(PsiFile psiFile) {
  }

  protected PsiFile createFileFromText(Project project, String text) {
    PsiFile psiFile = PsiFileFactory.getInstance(project)
      .createFileFromText("a." + getFileTypeExtension(getFileType()), getFileType(), text, LocalTimeCounter.currentTime(), true);
    return psiFile;
  }

  protected PsiFile doReformat(final Project project, final PsiFile psiFile) {
    CodeStyleManager.getInstance(project).reformat(psiFile);
    return psiFile;
  }

  protected Project getCurrentProject() {
    Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    return project;
  }

  @NotNull
  protected abstract FileType getFileType();

  @NonNls
  protected abstract String getPreviewText();

  public abstract void apply(CodeStyleSettings settings);

  public final void reset(final CodeStyleSettings settings) {
    myShouldUpdatePreview = false;
    try {
      resetImpl(settings);
    }
    finally {
      myShouldUpdatePreview = true;
    }
  }

  protected static int getIndexForWrapping(int value) {
    for (int i = 0; i < ourWrappings.length; i++) {
      int ourWrapping = ourWrappings[i];
      if (ourWrapping == value) return i;
    }
    LOG.assertTrue(false);
    return 0;
  }

  public abstract boolean isModified(CodeStyleSettings settings);

  public abstract JComponent getPanel();

  public void dispose() {
    myUpdateAlarm.cancelAllRequests();
    EditorFactory.getInstance().releaseEditor(myEditor);
  }

  protected abstract void resetImpl(final CodeStyleSettings settings);

  protected static void fillWrappingCombo(final JComboBox wrapCombo) {
    wrapCombo.addItem(ApplicationBundle.message("wrapping.do.not.wrap"));
    wrapCombo.addItem(ApplicationBundle.message("wrapping.wrap.if.long"));
    wrapCombo.addItem(ApplicationBundle.message("wrapping.chop.down.if.long"));
    wrapCombo.addItem(ApplicationBundle.message("wrapping.wrap.always"));
  }

  public static String readFromFile(final Class resourceContainerClass, @NonNls final String fileName) {
    try {
      final InputStream stream = resourceContainerClass.getClassLoader().getResourceAsStream("codeStyle/preview/" + fileName);
      final InputStreamReader reader = new InputStreamReader(stream);
      final LineNumberReader lineNumberReader = new LineNumberReader(reader);
      final StringBuffer result;
      try {
        result = new StringBuffer();
        String line;
        while ((line = lineNumberReader.readLine()) != null) {
          result.append(line);
          result.append("\n");
        }
      }
      finally {
        lineNumberReader.close();
      }

      return result.toString();
    }
    catch (IOException e) {
      return "";
    }
  }

  protected void installPreviewPanel(final JPanel previewPanel) {
    previewPanel.setLayout(new BorderLayout());
    previewPanel.add(myEditor.getComponent(), BorderLayout.CENTER);
  }

  @NonNls
  protected
  String getFileTypeExtension(FileType fileType) {
    return fileType.getDefaultExtension();
  }

  public void onSomethingChanged() {
    setSomethingChanged(true);
    UiNotifyConnector.doWhenFirstShown(myEditor.getComponent(), new Runnable(){
      public void run() {
        addUpdatePreviewRequest();
      }
    });
  }

  private void addUpdatePreviewRequest() {
    myUpdateAlarm.addComponentRequest(new Runnable() {
      public void run() {
        try {
          myUpdateAlarm.cancelAllRequests();
          if (isSomethingChanged()) {
            updatePreview();
          }
        }
        finally {
          setSomethingChanged(false);
        }
      }
    }, 300);
  }

  protected Editor getEditor() {
    return myEditor;
  }

  protected CodeStyleSettings getSettings() {
    return mySettings;
  }
}
