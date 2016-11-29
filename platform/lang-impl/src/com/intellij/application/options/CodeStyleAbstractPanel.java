/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.*;
import com.intellij.ui.UserActivityListener;
import com.intellij.ui.UserActivityWatcher;
import com.intellij.util.Alarm;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public abstract class CodeStyleAbstractPanel implements Disposable {

  private static final long TIME_TO_HIGHLIGHT_PREVIEW_CHANGES_IN_MILLIS = TimeUnit.SECONDS.toMillis(3);

  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.CodeStyleXmlPanel");

  private final List<TextRange>       myPreviewRangesToHighlight = new ArrayList<>();

  private final Editor myEditor;
  private final CodeStyleSettings mySettings;
  private boolean myShouldUpdatePreview;
  protected static final int[] ourWrappings =
    {CommonCodeStyleSettings.DO_NOT_WRAP, CommonCodeStyleSettings.WRAP_AS_NEEDED, CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM, CommonCodeStyleSettings.WRAP_ALWAYS};
  private long myLastDocumentModificationStamp;
  private String myTextToReformat = null;
  private final UserActivityWatcher myUserActivityWatcher = new UserActivityWatcher();

  private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  private CodeStyleSchemesModel myModel;
  private boolean mySomethingChanged = false;
  private long myEndHighlightPreviewChangesTimeMillis = -1;
  private boolean myShowsPreviewHighlighters;
  private final CodeStyleSettings myCurrentSettings;
  private final Language myDefaultLanguage;
  
  protected CodeStyleAbstractPanel(@NotNull CodeStyleSettings settings) {
    this(null, null, settings);
  }

  protected CodeStyleAbstractPanel(@Nullable Language defaultLanguage,
                                   @Nullable CodeStyleSettings currentSettings,
                                   @NotNull CodeStyleSettings settings)
  {
    myCurrentSettings = currentSettings;
    mySettings = settings;
    myDefaultLanguage = defaultLanguage;
    myEditor = createEditor();

    if (myEditor != null) {
      myUpdateAlarm.setActivationComponent(myEditor.getComponent());
    }
    myUserActivityWatcher.addUserActivityListener(new UserActivityListener() {
      @Override
      public void stateChanged() {
        somethingChanged();
      }
    });

    updatePreview(true);
  }

  protected void setShouldUpdatePreview(boolean shouldUpdatePreview) {
    myShouldUpdatePreview = shouldUpdatePreview;
  }

  private synchronized void setSomethingChanged(final boolean b) {
    mySomethingChanged = b;
  }

  private synchronized boolean isSomethingChanged() {
    return mySomethingChanged;
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

  @Nullable
  private Editor createEditor() {
    if (getPreviewText() == null) return null;
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document editorDocument = editorFactory.createDocument("");
    EditorEx editor = (EditorEx)editorFactory.createEditor(editorDocument);
    fillEditorSettings(editor.getSettings());
    myLastDocumentModificationStamp = editor.getDocument().getModificationStamp();
    return editor;
  }

  private static void fillEditorSettings(final EditorSettings editorSettings) {
    editorSettings.setWhitespacesShown(true);
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setIndentGuidesShown(false);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setFoldingOutlineShown(false);
    editorSettings.setAdditionalColumnsCount(0);
    editorSettings.setAdditionalLinesCount(1);
    editorSettings.setUseSoftWraps(false);
  }

  protected void updatePreview(boolean useDefaultSample) {
    if (myEditor == null) return;
    updateEditor(useDefaultSample);
    updatePreviewHighlighter((EditorEx)myEditor);
  }

  private void updateEditor(boolean useDefaultSample) {
    if (!myShouldUpdatePreview || (!ApplicationManager.getApplication().isUnitTestMode() && !myEditor.getComponent().isShowing())) {
      return;
    }

    if (myLastDocumentModificationStamp != myEditor.getDocument().getModificationStamp()) {
      myTextToReformat = myEditor.getDocument().getText();
    }
    else if (useDefaultSample || myTextToReformat == null) {
      myTextToReformat = getPreviewText();
    }

    int currOffs = myEditor.getScrollingModel().getVerticalScrollOffset();

    final Project finalProject = ProjectUtil.guessCurrentProject(getPanel());
    CommandProcessor.getInstance().executeCommand(finalProject, () -> replaceText(finalProject), null, null);

    myEditor.getSettings().setRightMargin(getAdjustedRightMargin());
    myLastDocumentModificationStamp = myEditor.getDocument().getModificationStamp();
    myEditor.getScrollingModel().scrollVertically(currOffs);
  }

  private int getAdjustedRightMargin() {
    int result = getRightMargin();
    return result > 0 ? result : CodeStyleFacade.getInstance(ProjectUtil.guessCurrentProject(getPanel())).getRightMargin(getDefaultLanguage());
  }

  protected abstract int getRightMargin();

  private void replaceText(final Project project) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        Document beforeReformat = null;
        if (myEditor.getDocument().getTextLength() > 0) {
          beforeReformat = collectChangesBeforeCurrentSettingsAppliance(project);
        }

        //important not mark as generated not to get the classes before setting language level
        PsiFile psiFile = createFileFromText(project, myTextToReformat);
        prepareForReformat(psiFile);

        try {
          apply(mySettings);
        }
        catch (ConfigurationException ignore) {
        }
        CodeStyleSettings clone = mySettings.clone();
        clone.setRightMargin(getDefaultLanguage(), getAdjustedRightMargin());
        CodeStyleSettingsManager.getInstance(project).setTemporarySettings(clone);
        PsiFile formatted;
        try {
          formatted = doReformat(project, psiFile);
        }
        finally {
          CodeStyleSettingsManager.getInstance(project).dropTemporarySettings();
        }

        myEditor.getSettings().setTabSize(clone.getTabSize(getFileType()));
        Document document = myEditor.getDocument();
        document.replaceString(0, document.getTextLength(), formatted.getText());
        if (beforeReformat != null) {
          highlightChanges(beforeReformat);
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    });
  }

  /**
   * Reformats {@link #myTextToReformat target text} with the {@link #mySettings current code style settings} and returns
   * list of changes applied to the target text during that.
   *
   * @param project   project to use
   * @return          list of changes applied to the {@link #myTextToReformat target text} during reformatting. It is sorted
   *                  by change start offset in ascending order
   */
  @Nullable
  private Document collectChangesBeforeCurrentSettingsAppliance(Project project) {
    PsiFile psiFile = createFileFromText(project, myTextToReformat);
    prepareForReformat(psiFile);
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    if (documentManager != null) {
      Document document = documentManager.getDocument(psiFile);
      if (document != null) {
        CodeStyleSettings clone = mySettings.clone();
        clone.setRightMargin(getDefaultLanguage(), getAdjustedRightMargin());
        CodeStyleSettingsManager.getInstance(project).setTemporarySettings(clone);
        try {
          CodeStyleManager.getInstance(project).reformat(psiFile);
        }
        finally {
          CodeStyleSettingsManager.getInstance(project).dropTemporarySettings();
        }
        return document;
      }
    }
    return null;
  }

  protected void prepareForReformat(PsiFile psiFile) {
  }
  
  protected String getFileExt() {
    return getFileTypeExtension(getFileType());
  }

  protected PsiFile createFileFromText(Project project, String text) {
    return PsiFileFactory.getInstance(project).createFileFromText(
      "a." + getFileExt(), getFileType(), text, LocalTimeCounter.currentTime(), true
    );
  }

  protected PsiFile doReformat(final Project project, final PsiFile psiFile) {
    CodeStyleManager.getInstance(project).reformat(psiFile);
    return psiFile;
  }

  private void highlightChanges(Document beforeReformat) {
    myPreviewRangesToHighlight.clear();
    MarkupModel markupModel = myEditor.getMarkupModel();
    markupModel.removeAllHighlighters();

    myPreviewRangesToHighlight.addAll(ChangesDiffCalculator.calculateDiff(beforeReformat, myEditor.getDocument()));

    if (!myPreviewRangesToHighlight.isEmpty()) {
      myEndHighlightPreviewChangesTimeMillis = System.currentTimeMillis() + TIME_TO_HIGHLIGHT_PREVIEW_CHANGES_IN_MILLIS;
      myShowsPreviewHighlighters = true;
    }
  }

  /**
   * Allows to answer if particular visual position belongs to visual rectangle identified by the given visual position of
   * its top-left and bottom-right corners.
   *
   * @param targetPosition    position which belonging to target visual rectangle should be checked
   * @param startPosition     visual position of top-left corner of the target visual rectangle
   * @param endPosition       visual position of bottom-right corner of the target visual rectangle
   * @return                  <code>true</code> if given visual position belongs to the target visual rectangle;
   *                          <code>false</code> otherwise
   */
  private static boolean isWithinBounds(VisualPosition targetPosition, VisualPosition startPosition, VisualPosition endPosition) {
    return targetPosition.line >= startPosition.line && targetPosition.line <= endPosition.line
           && targetPosition.column >= startPosition.column && targetPosition.column <= endPosition.column;
  }

  private void updatePreviewHighlighter(final EditorEx editor) {
    EditorColorsScheme scheme = editor.getColorsScheme();
    editor.getSettings().setCaretRowShown(false);
    editor.setHighlighter(createHighlighter(scheme));
  }

  @Nullable
  protected abstract EditorHighlighter createHighlighter(final EditorColorsScheme scheme);

  @NotNull
  protected abstract FileType getFileType();

  @NonNls
  @Nullable
  protected abstract String getPreviewText();

  public abstract void apply(CodeStyleSettings settings) throws ConfigurationException;

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

  @Nullable
  public abstract JComponent getPanel();

  @Override
  public void dispose() {
    myUpdateAlarm.cancelAllRequests();
    if (myEditor != null) {
      EditorFactory.getInstance().releaseEditor(myEditor);
    }
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
      final StringBuffer result;
      final LineNumberReader lineNumberReader = new LineNumberReader(reader);
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
    previewPanel.add(getEditor().getComponent(), BorderLayout.CENTER);
    previewPanel.setBorder(new AbstractBorder() {
      private static final int LEFT_WHITE_SPACE = 2;

      @Override
      public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Editor editor = getEditor();
        if (editor instanceof EditorEx) {
          g.setColor(((EditorEx)editor).getBackgroundColor());
          g.fillRect(x + 1, y, LEFT_WHITE_SPACE, height);
        }
        g.setColor(OnePixelDivider.BACKGROUND);
        g.fillRect(x, y, 1, height);
      }

      @Override
      public Insets getBorderInsets(Component c, Insets insets) {
        insets.set(0, 1 + LEFT_WHITE_SPACE, 0, 0);
        return insets;
      }
    });
  }

  @NonNls
  protected
  String getFileTypeExtension(FileType fileType) {
    return fileType.getDefaultExtension();
  }

  public void onSomethingChanged() {
    setSomethingChanged(true);
    if (myEditor != null) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        updateEditor(true);
      }
      else {
        UiNotifyConnector.doWhenFirstShown(myEditor.getComponent(), () -> addUpdatePreviewRequest());
      }
    }
  }

  private void addUpdatePreviewRequest() {
    myUpdateAlarm.addComponentRequest(new Runnable() {
      @Override
      public void run() {
        try {
          myUpdateAlarm.cancelAllRequests();
          if (isSomethingChanged()) {
            updateEditor(false);
          }
          if (System.currentTimeMillis() <= myEndHighlightPreviewChangesTimeMillis && !myPreviewRangesToHighlight.isEmpty()) {
            blinkHighlighters();
            myUpdateAlarm.addComponentRequest(this, 500);
          }
          else {
            myEditor.getMarkupModel().removeAllHighlighters();
          }
        }
        finally {
          setSomethingChanged(false);
        }
      }
    }, 300);
  }

  private void blinkHighlighters() {
    MarkupModel markupModel = myEditor.getMarkupModel();
    if (myShowsPreviewHighlighters) {
      Rectangle visibleArea = myEditor.getScrollingModel().getVisibleArea();
      VisualPosition visualStart = myEditor.xyToVisualPosition(visibleArea.getLocation());
      VisualPosition visualEnd = myEditor.xyToVisualPosition(new Point(visibleArea.x + visibleArea.width, visibleArea.y + visibleArea.height));

      // There is a possible case that viewport is located at its most bottom position and last document symbol
      // is located at the start of the line, hence, resulting visual end column has a small value and doesn't actually
      // indicates target visible rectangle. Hence, we need to correct that if necessary.
      int endColumnCandidate = visibleArea.width / EditorUtil.getSpaceWidth(Font.PLAIN, myEditor) + visualStart.column;
      if (endColumnCandidate > visualEnd.column) {
        visualEnd = new VisualPosition(visualEnd.line, endColumnCandidate);
      }
      int offsetToScroll = -1;
      CharSequence text = myEditor.getDocument().getCharsSequence();
      TextAttributes backgroundAttributes = myEditor.getColorsScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      TextAttributes borderAttributes = new TextAttributes(
        null, null, backgroundAttributes.getBackgroundColor(), EffectType.BOXED, Font.PLAIN
      );
      boolean scrollToChange = true;
      for (TextRange range : myPreviewRangesToHighlight) {
        if (scrollToChange) {
          boolean rangeVisible = isWithinBounds(myEditor.offsetToVisualPosition(range.getStartOffset()), visualStart, visualEnd)
                                 || isWithinBounds(myEditor.offsetToVisualPosition(range.getEndOffset()), visualStart, visualEnd);
          scrollToChange = !rangeVisible;
          if (offsetToScroll < 0) {
            if (text.charAt(range.getStartOffset()) != '\n') {
              offsetToScroll = range.getStartOffset();
            }
            else if (range.getEndOffset() > 0 && text.charAt(range.getEndOffset() - 1) != '\n') {
              offsetToScroll = range.getEndOffset() - 1;
            }
          }
        }

        TextAttributes attributesToUse = range.getLength() > 0 ? backgroundAttributes : borderAttributes;
        markupModel.addRangeHighlighter(
          range.getStartOffset(), range.getEndOffset(), HighlighterLayer.SELECTION, attributesToUse, HighlighterTargetArea.EXACT_RANGE
        );
      }

      if (scrollToChange) {
        if (offsetToScroll < 0 && !myPreviewRangesToHighlight.isEmpty()) {
          offsetToScroll = myPreviewRangesToHighlight.get(0).getStartOffset();
        }
        if (offsetToScroll >= 0 && offsetToScroll < text.length() - 1 && text.charAt(offsetToScroll) != '\n') {
          // There is a possible case that target offset is located too close to the right edge. However, our point is to show
          // highlighted region at target offset, hence, we need to scroll to the visual symbol end. Hence, we're trying to ensure
          // that by scrolling to the symbol's end over than its start.
          offsetToScroll++;
        }
        if (offsetToScroll >= 0 && offsetToScroll < myEditor.getDocument().getTextLength()) {
          myEditor.getScrollingModel().scrollTo(
            myEditor.offsetToLogicalPosition(offsetToScroll), ScrollType.RELATIVE
          );
        }
      }
    }
    else {
      markupModel.removeAllHighlighters();
    }
    myShowsPreviewHighlighters = !myShowsPreviewHighlighters;
  }

  protected Editor getEditor() {
    return myEditor;
  }

  @NotNull
  protected CodeStyleSettings getSettings() {
    return mySettings;
  }

  public Set<String> processListOptions() {
    return Collections.emptySet();
  }
  
  public final void applyPredefinedSettings(@NotNull PredefinedCodeStyle codeStyle) {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(ProjectUtil.guessCurrentProject(getPanel())).clone();
    codeStyle.apply(settings);
    reset(settings);
    onSomethingChanged();    
  }

  /**
   * Override this method if the panel is linked to a specific language. 
   * @return The language this panel is associated with.
   */
  @Nullable
  public Language getDefaultLanguage()  {
    return myDefaultLanguage;
  }

  protected String getTabTitle() {
    return "Other";
  }
  
  protected CodeStyleSettings getCurrentSettings() {
    return myCurrentSettings;
  }
  
  public void setupCopyFromMenu(JPopupMenu copyMenu) {
    copyMenu.removeAll();
  }
  
  public boolean isCopyFromMenuAvailable() {
    return false;
  }

}
