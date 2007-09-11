/**
 * @author cdr
 */
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.impl.FileTemplateConfigurable;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.ide.util.TreeFileChooser;
import com.intellij.lang.properties.psi.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.ui.*;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class I18nizeQuickFixDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.i18n.I18nizeQuickFixDialog");

  private JTextField myValue;
  private JComboBox myKey;
  private TextFieldWithHistory myPropertiesFile;
  private JPanel myPanel;
  private JCheckBox myUseResourceBundle;
  private final Project myProject;
  private final PsiFile myContext;
  private final PsiLiteralExpression myLiteralExpression;
  private JPanel myPropertiesFilePanel;
  private JLabel myPreviewLabel;
  private JPanel myHyperLinkPanel;
  private JPanel myResourceBundleSuggester;
  private EditorComboBox myRBEditorTextField;
  private JPanel myJavaCodeInfoPanel;
  private JPanel myPreviewPanel;
  private PsiClassType myResourceBundleType;
  private final String myDefaultPropertyValue;
  private final boolean myShowJavaCodeInfo;
  private final boolean myShowPreview;
  protected ResourceBundleManager myResourceBundleManager;

  @NonNls private static final String PROPERTY_KEY_OPTION_KEY = "PROPERTY_KEY";
  @NonNls private static final String RESOURCE_BUNDLE_OPTION_KEY = "RESOURCE_BUNDLE";
  @NonNls private static final String PROPERTY_VALUE_ATTR = "PROPERTY_VALUE";

  public I18nizeQuickFixDialog(@NotNull Project project,
                               @NotNull final PsiFile context,
                               @Nullable final PsiLiteralExpression literalExpression,
                               String defaultPropertyValue,
                               final boolean showJavaCodeInfo,
                               final boolean showPreview) {
    super(false);
    myProject = project;
    myContext = context;
    myLiteralExpression = literalExpression;

    myDefaultPropertyValue = defaultPropertyValue;

    myShowPreview = showPreview;

    setTitle(CodeInsightBundle.message("i18nize.dialog.title"));

    myResourceBundleSuggester.setLayout(new BorderLayout());
    PsiManager psiManager = PsiManager.getInstance(myProject);
    PsiElementFactory factory = psiManager.getElementFactory();
    PsiClass resourceBundle = null;
    try {
      myResourceBundleManager = ResourceBundleManager.getManager(context);
      LOG.assertTrue(myResourceBundleManager != null);
      resourceBundle = myResourceBundleManager.getResourceBundle();
    }
    catch (ResourceBundleManager.ResourceBundleNotFoundException e) {
      //can't be
    }
    myShowJavaCodeInfo = showJavaCodeInfo && myResourceBundleManager.canShowJavaCodeInfo();
    if (myShowJavaCodeInfo) {
      LOG.assertTrue(resourceBundle != null);
      myResourceBundleType = factory.createType(resourceBundle);
      @NonNls String defaultVarName = "resourceBundle";
      PsiExpressionCodeFragment expressionCodeFragment =
        factory.createExpressionCodeFragment(defaultVarName, myLiteralExpression, myResourceBundleType, true);
      Document document = PsiDocumentManager.getInstance(myProject).getDocument(expressionCodeFragment);
      myRBEditorTextField = new EditorComboBox(document, myProject, StdFileTypes.JAVA);
      myResourceBundleSuggester.add(myRBEditorTextField, BorderLayout.CENTER);
      suggestAvailableResourceBundleExpressions();
      myRBEditorTextField.addDocumentListener(new com.intellij.openapi.editor.event.DocumentAdapter() {
        public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent e) {
          somethingChanged();
        }
      });
    }

    myPropertiesFile = new TextFieldWithHistory();
    myPropertiesFilePanel.add(GuiUtils.constructFieldWithBrowseButton(myPropertiesFile, new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        TreeClassChooserFactory chooserFactory = TreeClassChooserFactory.getInstance(myProject);
        TreeFileChooser fileChooser = chooserFactory.createFileChooser(
          CodeInsightBundle.message("i18nize.dialog.property.file.chooser.title"), getPropertiesFile(), StdFileTypes.PROPERTIES, null);
        fileChooser.showDialog();
        PsiFile selectedFile = fileChooser.getSelectedFile();
        if (selectedFile == null) return;
        myPropertiesFile.setText(FileUtil.toSystemDependentName(selectedFile.getVirtualFile().getPath()));
      }
    }), BorderLayout.CENTER);
    populatePropertiesFiles();

    myPropertiesFile.addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        propertiesFileChanged();
        somethingChanged();
      }
    });

    getKeyTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        somethingChanged();
      }
    });

    myValue.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        somethingChanged();
      }
    });

    myHyperLinkPanel.setLayout(new BorderLayout());
    final String templateName = getTemplateName();
    if (templateName != null) {
      HyperlinkLabel link = new HyperlinkLabel(CodeInsightBundle.message("i18nize.dialog.template.link.label"));
      link.addHyperlinkListener(new HyperlinkListener() {
        public void hyperlinkUpdate(HyperlinkEvent e) {
          final FileTemplateConfigurable configurable = new FileTemplateConfigurable();
          final FileTemplate template = FileTemplateManager.getInstance().getCodeTemplate(templateName);
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              configurable.setTemplate(template, null);
            }
          });
          boolean ok = ShowSettingsUtil.getInstance().editConfigurable(myPanel, configurable);
          if (ok) {
            somethingChanged();
            if (myShowJavaCodeInfo) {
              suggestAvailableResourceBundleExpressions();
            }
          }
        }
      });
      myHyperLinkPanel.add(link, BorderLayout.CENTER);
    }

    if (!myShowJavaCodeInfo) {
      myJavaCodeInfoPanel.setVisible(false);
    }
    if (!myShowPreview) {
      myPreviewPanel.setVisible(false);
    }
    @NonNls final String KEY = "I18NIZE_DIALOG_USE_RESOURCE_BUNDLE";
    final boolean useBundleByDefault =
      !PropertiesComponent.getInstance().isValueSet(KEY) || PropertiesComponent.getInstance().isTrueValue(KEY);
    myUseResourceBundle.setSelected(useBundleByDefault);
    myUseResourceBundle.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        PropertiesComponent.getInstance().setValue(KEY, Boolean.valueOf(myUseResourceBundle.isSelected()).toString());
      }
    });

    propertiesFileChanged();
    somethingChanged();
    setKeyValueEditBoxes();

    init();
  }

  public static boolean isAvailable(PsiFile file) {
    final Project project = file.getProject();
    final String title = CodeInsightBundle.message("i18nize.dialog.error.jdk.title");
    try {
      return ResourceBundleManager.getManager(file) != null;
    }
    catch (ResourceBundleManager.ResourceBundleNotFoundException e) {
      final IntentionAction fix = e.getFix();
      if (fix != null) {
        if (Messages.showOkCancelDialog(project, e.getMessage(), title, Messages.getErrorIcon()) == DialogWrapper.OK_EXIT_CODE) {
          try {
            fix.invoke(project, null, file);
            return false;
          }
          catch (IncorrectOperationException e1) {
            LOG.error(e1);
          }
        }
      }
      Messages.showErrorDialog(project, e.getMessage(), title);
      return false;
    }
  }

  private JTextField getKeyTextField() {
    return (JTextField)myKey.getEditor().getEditorComponent();
  }

  public PropertyCreationHandler getPropertyCreationHandler() {
    PropertyCreationHandler handler = myResourceBundleManager.getPropertyCreationHandler();
    return handler != null ? handler : I18nUtil.DEFAULT_PROPERTY_CREATION_HANDLER;
  }

  @Nullable
  protected String getTemplateName() {
    return myResourceBundleManager.getTemplateName();
  }

  private void suggestAvailableResourceBundleExpressions() {
    String templateName = getTemplateName();
    if (templateName == null) return;

    if (myShowJavaCodeInfo) {
      FileTemplate template = FileTemplateManager.getInstance().getCodeTemplate(templateName);
      boolean showResourceBundleSuggester = template.getText().contains("${" + RESOURCE_BUNDLE_OPTION_KEY + "}");
      myJavaCodeInfoPanel.setVisible(showResourceBundleSuggester);
    }
    Set<String> result = I18nUtil.suggestExpressionOfType(myResourceBundleType, myLiteralExpression);
    if (result.isEmpty()) {
      result.add(getResourceBundleText());
    }

    myRBEditorTextField.setHistory(result.toArray(new String[result.size()]));
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myRBEditorTextField.setSelectedIndex(0);
      }
    });
  }

  @NotNull
  protected List<String> getExistingValueKeys(String value) {
    final ArrayList<String> result = new ArrayList<String>();

    // check if property value already exists among properties file values and suggest corresponding key
    PropertiesFile propertiesFile = getPropertiesFile();
    if (propertiesFile != null) {
      for (Property property : propertiesFile.getProperties()) {
        if (Comparing.strEqual(property.getValue(), value)) {
          result.add(0, property.getUnescapedKey());
        }
      }
    }
    return result;
  }

  protected String suggestPropertyKey(String value) {
    // suggest property key not existing in this file
    final StringBuilder result = new StringBuilder();
    boolean insertDotBeforeNextWord = false;
    for (int i = 0; i < value.length(); i++) {
      final char c = value.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        if (insertDotBeforeNextWord) {
          result.append('.');
        }
        result.append(Character.toLowerCase(c));
        insertDotBeforeNextWord = false;
      }
      else if (c == '&') {   //do not insert dot if there is letter after the amp
        if (insertDotBeforeNextWord) continue;
        if (i == value.length() - 1) {
          continue;
        }
        if (Character.isLetter(value.charAt(i + 1))) {
          continue;
        }
        insertDotBeforeNextWord = true;
      }
      else {
        if (result.length() > 0) {
          insertDotBeforeNextWord = true;
        }
      }
    }
    value = result.toString();

    PropertiesFile propertiesFile = getPropertiesFile();
    if (propertiesFile != null) {
      if (propertiesFile.findPropertyByKey(value) == null) return value;

      int suffix = 1;
      while (propertiesFile.findPropertyByKey(value + suffix) != null) {
        suffix++;
      }
      return value + suffix;
    }
    else {
      return value;
    }
  }

  private void propertiesFileChanged() {
    PropertiesFile propertiesFile = getPropertiesFile();
    boolean hasResourceBundle =
      propertiesFile != null && propertiesFile.getResourceBundle().getPropertiesFiles(propertiesFile.getProject()).size() > 1;
    myUseResourceBundle.setEnabled(hasResourceBundle);
  }

  private void setKeyValueEditBoxes() {
    final List<String> existingValueKeys = getExistingValueKeys(myDefaultPropertyValue);

    if (existingValueKeys.isEmpty()) {
      getKeyTextField().setText(suggestPropertyKey(myDefaultPropertyValue));
    }
    else {
      for (String key : existingValueKeys) {
        myKey.addItem(key);
      }
      myKey.setSelectedItem(existingValueKeys.get(0));
    }


    myValue.setText(myDefaultPropertyValue);
  }

  private void somethingChanged() {
    if (myShowPreview) {
      myPreviewLabel.setText(getI18nizedText());
    }
    setOKActionEnabled(!StringUtil.isEmptyOrSpaces(getKey()));
  }

  public String getI18nizedText() {
    String propertyKey = StringUtil.escapeStringCharacters(getKey());
    I18nizedTextGenerator textGenerator = myResourceBundleManager.getI18nizedTextGenerator();
    if (textGenerator != null) {
      return generateText(textGenerator, propertyKey, getPropertiesFile(), myLiteralExpression);
    }

    String templateName = getTemplateName();
    LOG.assertTrue(templateName != null);
    FileTemplate template = FileTemplateManager.getInstance().getCodeTemplate(templateName);
    Map<String, String> attributes = new THashMap<String, String>();
    attributes.put(PROPERTY_KEY_OPTION_KEY, propertyKey);
    attributes.put(RESOURCE_BUNDLE_OPTION_KEY, getResourceBundleText());
    addAdditionalAttributes(attributes);
    attributes.put(PROPERTY_VALUE_ATTR, StringUtil.escapeStringCharacters(myDefaultPropertyValue));
    String text = null;
    try {
      text = template.getText(attributes);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return text;
  }

  protected String generateText(final I18nizedTextGenerator textGenerator,
                                final String propertyKey,
                                final PropertiesFile propertiesFile,
                                final PsiLiteralExpression literalExpression) {
    return textGenerator.getI18nizedText(propertyKey, propertiesFile, literalExpression);
  }

  protected void addAdditionalAttributes(final Map<String, String> attributes) {
  }

  private void populatePropertiesFiles() {
    List<String> paths = suggestPropertiesFiles();
    Collections.sort(paths);
    myPropertiesFile.setHistory(paths);
    String lastUrl = suggestLastSelectedFileUrl();
    if (lastUrl != null) {
      String path = FileUtil.toSystemDependentName(VfsUtil.urlToPath(lastUrl));
      myPropertiesFile.setSelectedItem(path);
    }
    if (myPropertiesFile.getSelectedIndex() == -1 && !paths.isEmpty()) {
      myPropertiesFile.setText(paths.get(0));
    }
  }

  private String suggestLastSelectedFileUrl() {
    return LastSelectedPropertiesFileStore.getInstance().suggestLastSelectedPropertiesFileUrl(myContext);
  }

  private void saveLastSelectedFile() {
    PropertiesFile propertiesFile = getPropertiesFile();
    if (propertiesFile != null) {
      LastSelectedPropertiesFileStore.getInstance().saveLastSelectedPropertiesFile(myContext, propertiesFile);
    }
  }

  protected List<String> suggestPropertiesFiles() {
    return myResourceBundleManager.suggestPropertiesFiles();
  }

  private PropertiesFile getPropertiesFile() {
    String path = FileUtil.toSystemIndependentName(myPropertiesFile.getText());
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
    if (virtualFile != null) {
      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);
      if (psiFile instanceof PropertiesFile) return (PropertiesFile)psiFile;
    }
    return null;
  }

  private boolean createPropertiesFileIfNotExists() {
    if (getPropertiesFile() != null) return true;
    final String path = FileUtil.toSystemIndependentName(myPropertiesFile.getText());
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(path);
    if (fileType != StdFileTypes.PROPERTIES) {
      String message = CodeInsightBundle.message("i18nize.cant.create.properties.file.because.its.name.is.associated",
                                                 myPropertiesFile.getText(), fileType.getDescription());
      Messages.showErrorDialog(myProject, message, CodeInsightBundle.message("i18nize.error.creating.properties.file"));
      return false;
    }

    final VirtualFile virtualFile;
    try {
      final File file = new File(path).getCanonicalFile();
      FileUtil.createParentDirs(file);
      final IOException[] e = new IOException[1];
      virtualFile = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
        public VirtualFile compute() {
          VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file.getParentFile());
          try {
            if (dir == null) {
              throw new IOException("Error creating directory structure for file '" + path + "'");
            }
            return dir.createChildData(this, file.getName());
          }
          catch (IOException e1) {
            e[0] = e1;
          }
          return null;
        }
      });
      if (e[0] != null) throw e[0];
    }
    catch (IOException e) {
      Messages.showErrorDialog(myProject, e.getLocalizedMessage(), CodeInsightBundle.message("i18nize.error.creating.properties.file"));
      return false;
    }

    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);
    return psiFile instanceof PropertiesFile;
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myKey;
  }

  public void dispose() {
    saveLastSelectedFile();
    super.dispose();
  }

  protected void doOKAction() {
    if (!createPropertiesFileIfNotExists()) return;
    Collection<PropertiesFile> propertiesFiles = getAllPropertiesFiles();
    for (PropertiesFile propertiesFile : propertiesFiles) {
      Property existingProperty = propertiesFile.findPropertyByKey(getKey());
      final String propValue = myValue.getText();
      if (existingProperty != null && !Comparing.strEqual(existingProperty.getValue(), propValue)) {
        Messages.showErrorDialog(myProject, CodeInsightBundle.message("i18nize.dialog.error.property.already.defined.message", getKey(),
                                                                      propertiesFile.getName()),
                                            CodeInsightBundle.message("i18nize.dialog.error.property.already.defined.title"));
        return;
      }
    }

    super.doOKAction();
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  public void doHelpAction() {
    HelpManager.getInstance().invokeHelp("editing.propertyFile.i18nInspection");
  }


  public JComponent getValueComponent() {
    return myValue;
  }

  public String getValue() {
    return myValue.getText();
  }

  public String getKey() {
    return getKeyTextField().getText();
  }

  private boolean isUseResourceBundle() {
    return myUseResourceBundle.isEnabled() && myUseResourceBundle.isSelected();
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.codeInsight.i18n.I18nizeQuickFixDialog";
  }

  public Collection<PropertiesFile> getAllPropertiesFiles() {
    PropertiesFile propertiesFile = getPropertiesFile();
    if (propertiesFile == null) return Collections.emptySet();
    Collection<PropertiesFile> propertiesFiles;
    if (isUseResourceBundle()) {
      propertiesFiles = propertiesFile.getResourceBundle().getPropertiesFiles(myProject);
    }
    else {
      propertiesFiles = Collections.singleton(propertiesFile);
    }
    return propertiesFiles;
  }

  private String getResourceBundleText() {
    return myShowJavaCodeInfo ? myRBEditorTextField.getText() : null;
  }

  public PsiLiteralExpression getLiteralExpression() {
    return myLiteralExpression;
  }

  public PsiExpression[] getParameters() {
    return PsiExpression.EMPTY_ARRAY;
  }
}
