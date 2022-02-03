// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates.impl;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.ex.util.LayerDescriptor;
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.tree.TokenSet;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public class FileTemplateConfigurable implements Configurable, Configurable.NoScroll {
  private static final Logger LOG = Logger.getInstance(FileTemplateConfigurable.class);
  @NonNls private static final String EMPTY_HTML = "<html></html>";

  private JPanel myMainPanel;
  private FileTemplate myTemplate;
  private Editor myTemplateEditor;
  private JTextField myNameField;
  private JTextField myExtensionField;
  private JCheckBox myAdjustBox;
  private JCheckBox myLiveTemplateBox;
  private JPanel myTopPanel;
  private EditorTextField myFileName;
  private JEditorPane myDescriptionComponent;
  private boolean myModified;
  private URL myDefaultDescriptionUrl;
  private final Project myProject;

  private final List<ChangeListener> myChangeListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private Splitter mySplitter;
  private final FileType myVelocityFileType = FileTypeManager.getInstance().getFileTypeByExtension("ft");
  private float myProportion = 0.6f;

  public FileTemplateConfigurable(Project project) {
    myProject = project;
  }

  public FileTemplate getTemplate() {
    return myTemplate;
  }

  public void setTemplate(@Nullable FileTemplate template, URL defaultDescription) {
    setTemplate(template, defaultDescription, false);
  }

  public void setTemplate(@Nullable FileTemplate template, URL defaultDescription, boolean internalTemplate) {
    myDefaultDescriptionUrl = defaultDescription;
    myTemplate = template;
    if (myMainPanel != null) {
      reset();
      updateTopPanel(internalTemplate);
      myNameField.selectAll();
      myExtensionField.selectAll();
      myFileName.setPlaceholder(IdeBundle.message(template != null && FileTemplateBase.isChild(template) ? "template.file.name" : "template.file.name.optional"));
    }
  }

  private void updateTopPanel(boolean internalTemplate) {
    myTopPanel.removeAll();
    if (!internalTemplate) {
      boolean child = myTemplate != null && FileTemplateBase.isChild(myTemplate);
      if (!child) {
        JLabel label = new JLabel(IdeBundle.message("label.name"));
        label.setLabelFor(myNameField);
        myTopPanel.add(label,
                       new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
                                              GridBagConstraints.NONE, JBInsets.emptyInsets(), 0, 0));
        myTopPanel.add(myNameField,
                       new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER,
                                              GridBagConstraints.HORIZONTAL, JBUI.insets(0, 3), 0, 0));
      }
      JLabel extLabel = new JLabel(IdeBundle.message("label.extension"));
      extLabel.setLabelFor(myExtensionField);
      myTopPanel.add(extLabel,
                     new GridBagConstraints(child ? 0 : 2, child ? 1 : 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER,
                                            GridBagConstraints.NONE, JBInsets.emptyInsets(), 0, 0));
      myTopPanel.add(myExtensionField,
                     new GridBagConstraints(child ? 1 : 3, child ? 1 : 0, 1, 1, .3, 0.0, GridBagConstraints.WEST,
                                            child ? GridBagConstraints.NONE : GridBagConstraints.HORIZONTAL, JBUI.insetsLeft(3), 0, 0));
      if (child || (isEditable() || StringUtil.isNotEmpty(myFileName.getText()))) {
        JLabel label = new JLabel(IdeBundle.message("label.generate.file.name"));
        label.setLabelFor(myFileName);
        myTopPanel.add(label,
                       new GridBagConstraints(0, child ? 0 : 1, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
                                              GridBagConstraints.NONE, JBInsets.emptyInsets(), 0, 0));
        myTopPanel.add(myFileName,
                       new GridBagConstraints(1, child ? 0 : 1, 3, 1, 1.0, 0.0, GridBagConstraints.CENTER,
                                              GridBagConstraints.HORIZONTAL, JBUI.insetsLeft(3), 0, 0));
      }
    }
    myMainPanel.revalidate();
    myTopPanel.repaint();
  }

  void setShowAdjustCheckBox(boolean show) {
    myAdjustBox.setEnabled(show);
  }

  @Override
  public String getDisplayName() {
    return IdeBundle.message("title.edit.file.template");
  }

  @Override
  public String getHelpTopic() {
    return null;
  }

  @Override
  public JComponent createComponent() {
    myMainPanel = new JPanel(new GridBagLayout());
    myNameField = new JTextField();
    myExtensionField = new JTextField(7);
    mySplitter = new Splitter(true, myProportion);
    myAdjustBox = new JCheckBox(IdeBundle.message("checkbox.reformat.according.to.style"));
    myLiveTemplateBox = new JCheckBox(IdeBundle.message("checkbox.enable.live.templates"));

    myTemplateEditor = createEditor(null);
    myFileName = new EditorTextField(createDocument(createFile("", "file name")), myProject, myVelocityFileType);
    myFileName.setFont(EditorUtil.getEditorFont());
    myFileName.setPlaceholder(IdeBundle.message("template.file.name"));
    myFileName.setShowPlaceholderWhenFocused(true);

    myDescriptionComponent = new JEditorPane();
    myDescriptionComponent.setEditorKit(HTMLEditorKitBuilder.simple());
    myDescriptionComponent.setText(EMPTY_HTML);
    myDescriptionComponent.setEditable(false);
    myDescriptionComponent.addHyperlinkListener(new BrowserHyperlinkListener());

    myTopPanel = new JPanel(new GridBagLayout());
    myTopPanel.setBorder(JBUI.Borders.emptyBottom(3));

    JPanel descriptionPanel = new JPanel(new GridBagLayout());
    descriptionPanel.add(new JLabel(IdeBundle.message("label.description")),
                         new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                                                JBUI.insetsBottom(2), 0, 0));
    descriptionPanel.add(ScrollPaneFactory.createScrollPane(myDescriptionComponent),
                         new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                JBUI.insetsTop(2), 0, 0));

    myMainPanel.add(myTopPanel,
                    new GridBagConstraints(0, 0, 4, 1, 1.0, 0.0, GridBagConstraints.CENTER,
                                           GridBagConstraints.HORIZONTAL, JBInsets.emptyInsets(), 0, 0));
    myMainPanel.add(mySplitter,
                    new GridBagConstraints(0, 2, 4, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                           JBInsets.emptyInsets(), 0, 0));

    mySplitter.setSecondComponent(descriptionPanel);
    updateTopPanel(false);

    myNameField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(@NotNull FocusEvent e) {
        onNameChanged();
      }
    });
    myExtensionField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(@NotNull FocusEvent e) {
        onNameChanged();
      }
    });
    myMainPanel.setPreferredSize(JBUI.size(400, 300));
    return myMainPanel;
  }

  public void setProportion(float proportion) {
    myProportion = proportion;
  }

  private Editor createEditor(@Nullable PsiFile file) {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document doc = createDocument(file);
    Editor editor = editorFactory.createEditor(doc, myProject);

    EditorSettings editorSettings = editor.getSettings();
    editorSettings.setVirtualSpace(false);
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setIndentGuidesShown(false);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setFoldingOutlineShown(false);
    editorSettings.setAdditionalColumnsCount(3);
    editorSettings.setAdditionalLinesCount(3);
    editorSettings.setCaretRowShown(false);

    editor.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        onTextChanged();
      }
    }, ((EditorImpl)editor).getDisposable());

    ((EditorEx)editor).setHighlighter(createHighlighter());

    JPanel topPanel = new JPanel(new BorderLayout(0, 5));
    JPanel southPanel = new JPanel(new HorizontalLayout(40));
    southPanel.add(myAdjustBox);
    southPanel.add(myLiveTemplateBox);

    topPanel.add(southPanel, BorderLayout.SOUTH);
    topPanel.add(editor.getComponent(), BorderLayout.CENTER);
    mySplitter.setFirstComponent(topPanel);
    return editor;
  }

  @NotNull
  private Document createDocument(@Nullable PsiFile file) {
    Document document = file != null ? PsiDocumentManager.getInstance(file.getProject()).getDocument(file) : null;
    return document != null ? document : EditorFactory.getInstance().createDocument(myTemplate == null ? "" : myTemplate.getText());
  }

  private void onTextChanged() {
    myModified = true;
  }

  private void onNameChanged() {
    ChangeEvent event = new ChangeEvent(this);
    for (ChangeListener changeListener : myChangeListeners) {
      changeListener.stateChanged(event);
    }
  }

  void addChangeListener(@NotNull ChangeListener listener) {
    if (!myChangeListeners.contains(listener)) {
      myChangeListeners.add(listener);
    }
  }

  public void removeChangeListener(ChangeListener listener) {
    myChangeListeners.remove(listener);
  }

  @Override
  public boolean isModified() {
    if (myModified) {
      return true;
    }
    String name = myTemplate == null ? "" : myTemplate.getName();
    String extension = myTemplate == null ? "" : myTemplate.getExtension();
    if (!Objects.equals(name, myNameField.getText())) {
      return true;
    }
    if (!Objects.equals(extension, myExtensionField.getText())) {
      return true;
    }
    if (myTemplate != null) {
      if (myTemplate.isReformatCode() != myAdjustBox.isSelected() ||
          myTemplate.isLiveTemplateEnabled() != myLiveTemplateBox.isSelected() ||
          !myTemplate.getFileName().equals(myFileName.getText())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myTemplate != null) {
      myTemplate.setText(myTemplateEditor.getDocument().getText());
      String name = myNameField.getText();
      String extension = myExtensionField.getText();
      String filename = name + "." + extension;
      if (name.length() == 0 || !isValidFilename(filename)) {
        throw new ConfigurationException(IdeBundle.message("error.invalid.template.file.name.or.extension"));
      }
      FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(filename);
      if (fileType == UnknownFileType.INSTANCE) {
        FileTypeChooser.associateFileType(filename);
      }
      myTemplate.setName(name);
      myTemplate.setFileName(myFileName.getText());
      myTemplate.setExtension(extension);
      myTemplate.setReformatCode(myAdjustBox.isSelected());
      myTemplate.setLiveTemplateEnabled(myLiveTemplateBox.isSelected());
    }
    myModified = false;
  }

  // TODO: needs to be generalized someday for other profiles
  private static boolean isValidFilename(final String filename) {
    if ( filename.contains("/") || filename.contains("\\") || filename.contains(":") ) {
      return false;
    }
    final File tempFile = new File (FileUtil.getTempDirectory() + File.separator + filename);
    return FileUtil.ensureCanCreateFile(tempFile);
  }

  @Override
  public void reset() {
    final String text = myTemplate == null ? "" : myTemplate.getText();
    String name = myTemplate == null ? "" : myTemplate.getName();
    String extension = myTemplate == null ? "" : myTemplate.getExtension();
    String description = myTemplate == null ? "" : myTemplate.getDescription();

    if (description.isEmpty() && myDefaultDescriptionUrl != null) {
      try {
        description = UrlUtil.loadText(myDefaultDescriptionUrl); //NON-NLS
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    EditorFactory.getInstance().releaseEditor(myTemplateEditor);
    myTemplateEditor = createEditor(myTemplate == null ? null : createFile(text, name));

    myNameField.setText(name);
    myFileName.setText(myTemplate == null ? "" : myTemplate.getFileName());
    myExtensionField.setText(extension);
    myAdjustBox.setSelected(myTemplate != null && myTemplate.isReformatCode());
    myLiveTemplateBox.setSelected(myTemplate != null && myTemplate.isLiveTemplateEnabled());

    int i = description.indexOf("<html>");
    if (i > 0) {
      description = description.substring(i);
    }
    description = XmlStringUtil.stripHtml(description);
    description = description.replace("\n", "").replace("\r", "");
    description = XmlStringUtil.stripHtml(description);
    description = IdeBundle.message("http.velocity", description);

    myDescriptionComponent.setText(description);
    myDescriptionComponent.setCaretPosition(0);

    boolean editable = isEditable();
    myNameField.setEditable(editable);
    myExtensionField.setEditable(editable);
    myFileName.setViewer(!editable);
    myModified = false;
  }

  private boolean isEditable() {
    return myTemplate != null && !myTemplate.isDefault();
  }

  @Nullable
  private PsiFile createFile(final String text, final String name) {
    final FileType fileType = myVelocityFileType;
    if (fileType == FileTypes.UNKNOWN) return null;

    final PsiFile file = PsiFileFactory.getInstance(myProject).createFileFromText(name + ".txt.ft", fileType, text, 0, true);
    Properties properties = new Properties();
    properties.putAll(FileTemplateManager.getInstance(myProject).getDefaultProperties());
    properties.setProperty(FileTemplate.ATTRIBUTE_NAME, IdeBundle.message("name.variable"));
    file.getViewProvider().putUserData(FileTemplateManager.DEFAULT_TEMPLATE_PROPERTIES, properties);
    return file;
  }

  @Override
  public void disposeUIResources() {
    myMainPanel = null;
    if (myTemplateEditor != null) {
      EditorFactory.getInstance().releaseEditor(myTemplateEditor);
      myTemplateEditor = null;
    }
  }

  private EditorHighlighter createHighlighter() {
    if (myTemplate != null && myVelocityFileType != FileTypes.UNKNOWN) {
      return EditorHighlighterFactory.getInstance().createEditorHighlighter(myProject, new LightVirtualFile("aaa." + myTemplate.getExtension() + ".ft"));
    }

    FileType fileType = null;
    if (myTemplate != null) {
      fileType = FileTypeManager.getInstance().getFileTypeByExtension(myTemplate.getExtension());
    }
    if (fileType == null) {
      fileType = FileTypes.PLAIN_TEXT;
    }

    SyntaxHighlighter originalHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(fileType, null, null);
    if (originalHighlighter == null) {
      originalHighlighter = new PlainSyntaxHighlighter();
    }

    final EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    LayeredLexerEditorHighlighter highlighter = new LayeredLexerEditorHighlighter(new FileTemplateHighlighter(), scheme);
    highlighter.registerLayer(FileTemplateTokenType.TEXT, new LayerDescriptor(originalHighlighter, ""));
    return highlighter;
  }

  @NotNull
  @VisibleForTesting
  public static Lexer createDefaultLexer() {
    return new MergingLexerAdapter(new FlexAdapter(new _FileTemplateTextLexer()), TokenSet.create(FileTemplateTokenType.TEXT));
  }

  public void focusToNameField() {
    JComponent field = FileTemplateBase.isChild(myTemplate) ? myFileName : myNameField;
    myNameField.selectAll();
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(field, true));
  }
}
