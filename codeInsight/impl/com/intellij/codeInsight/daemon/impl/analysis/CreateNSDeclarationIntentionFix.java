package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.util.FQNameCellRenderer;
import com.intellij.j2ee.openapi.ex.ExternalResourceManagerEx;
import com.intellij.jsp.impl.TldDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.cache.impl.idCache.IdTableBuilding;
import com.intellij.psi.impl.source.jsp.JspManager;
import com.intellij.psi.jsp.JspDirectiveKind;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.meta.PsiMetaDataBase;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
* User: Maxim.Mossienko
* Date: Nov 29, 2007
* Time: 11:13:33 PM
* To change this template use File | Settings | File Templates.
*/
public class CreateNSDeclarationIntentionFix implements IntentionAction, LocalQuickFix {
  final boolean myTaglibDeclaration;
  private final XmlElement myElement;
  private final String myNamespacePrefix;
  @NonNls private static final String MY_DEFAULT_XML_NS = "someuri";
  @NonNls private static final String URI_ATTR_NAME = "uri";

  public CreateNSDeclarationIntentionFix(@NotNull final XmlElement element, @NotNull final String namespacePrefix, boolean taglibDeclaration) {
    myElement = element;
    myNamespacePrefix = namespacePrefix;
    myTaglibDeclaration = taglibDeclaration;
  }

  @NotNull
  public String getText() {
    return XmlErrorMessages.message(
      myTaglibDeclaration ?
      "create.taglib.declaration.quickfix":
      "create.namespace.declaration.quickfix"
    );
  }

  @NotNull
  public String getName() {
    return getFamilyName();
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiFile containingFile = descriptor.getPsiElement().getContainingFile();
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    final PsiFile file = editor != null ? PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()):null;
    if (file == null || file.getVirtualFile() != containingFile.getVirtualFile()) return;

    try { invoke(project, editor, containingFile); } catch (IncorrectOperationException ex) { ex.printStackTrace(); }
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  private String[] guessNamespace(final PsiFile file, final boolean acceptTaglib, final boolean acceptXmlNs) {
    final List<String> possibleUris = new LinkedList<String>();
    final ExternalUriProcessor processor = new ExternalUriProcessor() {
      public void process(String ns, final String url) {
        possibleUris.add(ns);
      }

      public boolean acceptXmlNs() {
        return acceptXmlNs;
      }

      public boolean acceptTaglib() {
        return acceptTaglib;
      }
    };

    if (myElement instanceof XmlTag) {
      processExternalUris(createMetaHandler((XmlTag)myElement), file, processor);
    }

    return possibleUris.toArray( new String[possibleUris.size()] );
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    final boolean taglib = myTaglibDeclaration || file instanceof JspFile;
    final String[] namespaces = guessNamespace(
      file, taglib,
      !(file instanceof JspFile)// || file.getFileType() == StdFileTypes.JSPX
    );

    runActionOverSeveralAttributeValuesAfterLettingUserSelectTheNeededOne(
      namespaces,
      project,
      new StringToAttributeProcessor() {
        @NotNull
        public TextRange doSomethingWithGivenStringToProduceXmlAttributeNowPlease(@NotNull final String attrName) throws IncorrectOperationException {
          final XmlAttribute attribute = insertNsDeclaration(file, attrName, project);
          final TextRange range = attribute.getValueTextRange();
          return range.shiftRight(attribute.getValueElement().getTextRange().getStartOffset());
        }
      },
      XmlErrorMessages.message(myTaglibDeclaration ? "select.taglib.title":"select.namespace.title"),
      this,
      editor,
      taglib ? XmlUtil.JSTL_CORE_URIS[0] : MY_DEFAULT_XML_NS
    );
  }

  private XmlAttribute insertNsDeclaration(final PsiFile file, final String namespace, final Project project)
    throws IncorrectOperationException {
    final XmlTag rootTag = ((XmlFile)file).getDocument().getRootTag();
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(rootTag.getProject()).getElementFactory();

    if (myTaglibDeclaration) {
      final XmlTag childTag = rootTag.createChildTag("directive.taglib", XmlUtil.JSP_URI, null, false);
      PsiElement element = childTag.add(elementFactory.createXmlAttribute("prefix", myNamespacePrefix));

      childTag.addAfter(
        elementFactory.createXmlAttribute(URI_ATTR_NAME,namespace),
        element
      );

      final XmlTag[] directives = ((JspFile)file).getDirectiveTags(JspDirectiveKind.TAGLIB, false);

      if (directives == null || directives.length == 0) {
        element = rootTag.addBefore(
          childTag, rootTag.getFirstChild()
        );
      } else {
        element = rootTag.addAfter(
          childTag, directives[directives.length - 1]
        );
      }

      CodeStyleManager.getInstance(project).reformat(element);
      return ((XmlTag)element).getAttribute(URI_ATTR_NAME,null);
    }
    else {
      @NonNls final String name = "xmlns" + (myNamespacePrefix.length() > 0 ? ":"+myNamespacePrefix:"");
      rootTag.add(
        elementFactory.createXmlAttribute(name,namespace)
      );
      return rootTag.getAttribute(name, null);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

  public interface StringToAttributeProcessor {
    @NotNull
    TextRange doSomethingWithGivenStringToProduceXmlAttributeNowPlease(@NotNull String attrName) throws IncorrectOperationException;
  }


  public static void runActionOverSeveralAttributeValuesAfterLettingUserSelectTheNeededOne(final @NotNull String[] namespacesToChooseFrom,
                                                                                 final Project project,
                                                                                 final StringToAttributeProcessor onSelection,
                           String title, final IntentionAction requestor, final Editor editor, String defaultValueForTestingWhenNoVariants) throws IncorrectOperationException {
    if (namespacesToChooseFrom.length > 1 && !ApplicationManager.getApplication().isUnitTestMode()) {
      final JList list = new JList(namespacesToChooseFrom);
      list.setCellRenderer(new FQNameCellRenderer());
      Runnable runnable = new Runnable() {
        public void run() {
          final int index = list.getSelectedIndex();
          if (index < 0) return;
          PsiDocumentManager.getInstance(project).commitAllDocuments();

          CommandProcessor.getInstance().executeCommand(
            project,
            new Runnable() {
              public void run() {
                ApplicationManager.getApplication().runWriteAction(
                  new Runnable() {
                    public void run() {
                      try {
                        onSelection.doSomethingWithGivenStringToProduceXmlAttributeNowPlease(namespacesToChooseFrom[index]);
                      } catch (IncorrectOperationException ex) {
                        throw new RuntimeException(ex);
                      }
                    }
                  }
                );
              }
            },
            requestor.getText(),
            requestor.getFamilyName()
          );
        }
      };

      new PopupChooserBuilder(list).
        setTitle(title).
        setItemChoosenCallback(runnable).
        createPopup().
        showInBestPositionFor(editor);
    } else {
      String defaultNs =
        ApplicationManager.getApplication().isUnitTestMode() ? defaultValueForTestingWhenNoVariants : "";
      final TextRange textRange = onSelection.doSomethingWithGivenStringToProduceXmlAttributeNowPlease(namespacesToChooseFrom.length > 0 ? namespacesToChooseFrom[0] : defaultNs);

      if (namespacesToChooseFrom.length == 0) {
        CommandProcessor.getInstance().executeCommand(
          project,
          new Runnable() {
            public void run() {
              if (textRange.getLength() != 0) {
                editor.getSelectionModel().setSelection(textRange.getStartOffset(), textRange.getEndOffset());
              }
              editor.getCaretModel().moveToOffset(textRange.getStartOffset());
            }
          },
          requestor.getText(),
          requestor.getFamilyName()
        );
      }
    }
  }

  public static void processExternalUris(@NotNull final XmlTag tag,
                                 final PsiFile file,
                                 final ExternalUriProcessor processor) {
    processExternalUris(createDefaultMetaHandlerStatic(tag), file, processor);
  }

  protected MetaHandler createMetaHandler(final XmlTag tag) {
    return createDefaultMetaHandlerStatic(tag);
  }

  private static MetaHandler createDefaultMetaHandlerStatic(final XmlTag tag) {
    return new TagMetaHandler(tag);
  }

  public static void processExternalUris(final MetaHandler metaHandler,
                                 final PsiFile file,
                                 final ExternalUriProcessor processor) {
    if (ApplicationManager.getApplication().isUnitTestMode()) processExternalUrisImpl(metaHandler, file, processor);
    else {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(
        new Runnable() {
          public void run() {
            processExternalUrisImpl(metaHandler, file, processor);
          }
        },
        XmlErrorMessages.message("finding.acceptable.uri"),
        false,
        file.getProject()
      );
    }
  }

  public interface MetaHandler {
    boolean isAcceptableMetaData(PsiMetaDataBase metadata, final String url);
    String searchFor();
  }

  public static class TagMetaHandler implements MetaHandler {
    private final XmlTag tag;

    public TagMetaHandler(final XmlTag tag) {
      this.tag = tag;
    }

    public boolean isAcceptableMetaData(final PsiMetaDataBase metaData, final String url) {
      if (metaData instanceof TldDescriptor) {
        if (((TldDescriptor)metaData).getElementDescriptor(tag) != null) return true;
        return false;
      } else if (metaData instanceof XmlNSDescriptorImpl) {
        final XmlNSDescriptorImpl nsDescriptor = (XmlNSDescriptorImpl)metaData;

        final XmlElementDescriptor descriptor = nsDescriptor.getElementDescriptor(searchFor(), url);
        return descriptor != null && !(descriptor instanceof AnyXmlElementDescriptor);
      }
      return false;
    }

    public String searchFor() {
      return tag.getLocalName();
    }
  }

  private static void processExternalUrisImpl(final MetaHandler metaHandler,
                                              final PsiFile file,
                                              final ExternalUriProcessor processor) {
    final ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();

    final JspManager jspManager = JspManager.getInstance(file.getProject());
    final String searchFor = metaHandler.searchFor();

    if (processor.acceptTaglib() && jspManager != null) {
      if (pi != null) pi.setText(XmlErrorMessages.message("looking.in.tlds"));
      final JspFile jspFile = (JspFile)file;
      final String[] possibleTldUris = jspManager.getPossibleTldUris(jspFile);

      Arrays.sort(possibleTldUris);
      int i = 0;

      boolean foundSomething = false;

      for (String uri : possibleTldUris) {
        if (pi != null) {
          pi.setFraction((double)i / possibleTldUris.length);
          pi.setText2(uri);
          ++i;
        }

        final XmlFile tldFileByUri = jspManager.getTldFileByUri(uri, jspFile);
        if (tldFileByUri == null) continue;

        final boolean wordFound = checkIfGivenXmlHasTheseWords(searchFor, tldFileByUri);
        if (!wordFound) continue;
        final PsiMetaDataBase metaData = tldFileByUri.getDocument().getMetaData();

        if (metaHandler.isAcceptableMetaData(metaData, uri)) {
          processor.process(uri, null);
          foundSomething = true;
        }
      }

      if (file.getFileType() == StdFileTypes.JSPX && !foundSomething) {
        final XmlNSDescriptorImpl nsDescriptor = (XmlNSDescriptorImpl)jspManager.getActionsLibrary(file);
        if (metaHandler.isAcceptableMetaData(nsDescriptor, XmlUtil.JSP_URI)) {
          processor.process(XmlUtil.JSP_URI, null);
        }
      }
    }

    if (processor.acceptXmlNs()) {
      if (pi != null) pi.setText(XmlErrorMessages.message("looking.in.schemas"));
      final ExternalResourceManagerEx instanceEx = ExternalResourceManagerEx.getInstanceEx();
      final String[] availableUrls = instanceEx.getResourceUrls(null, true);
      int i = 0;

      for (String url : availableUrls) {
        if (pi != null) {
          pi.setFraction((double)i / availableUrls.length);
          pi.setText2(url);
          ++i;
        }
        final XmlFile xmlFile = XmlUtil.findNamespace(file, url);

        if (xmlFile != null) {
          final boolean wordFound = checkIfGivenXmlHasTheseWords(searchFor, xmlFile);
          if (!wordFound) continue;
          final PsiMetaDataBase metaData = xmlFile.getDocument().getMetaData();

          if (metaHandler.isAcceptableMetaData(metaData, url)) {
            final XmlNSDescriptorImpl descriptor = metaData instanceof XmlNSDescriptorImpl ? (XmlNSDescriptorImpl)metaData:null;
            final String defaultNamespace = descriptor != null ? descriptor.getDefaultNamespace():url;

            // Skip rare stuff
            if (!XmlUtil.XML_SCHEMA_URI2.equals(defaultNamespace) && !XmlUtil.XML_SCHEMA_URI3.equals(defaultNamespace)) {
              processor.process(defaultNamespace, url);
            }
          }
        }
      }
    }
  }

  public static boolean checkIfGivenXmlHasTheseWords(final String name, final XmlFile tldFileByUri) {
    if (name == null || name.length() == 0) return true;
    final String[] words = StringUtil.getWordsIn(name).toArray(ArrayUtil.EMPTY_STRING_ARRAY);
    final boolean[] wordsFound = new boolean[words.length];
    final int[] wordsFoundCount = new int[1];

    IdTableBuilding.ScanWordProcessor wordProcessor = new IdTableBuilding.ScanWordProcessor() {
      public void run(final CharSequence chars, int start, int end, char[] charArray) {
        if (wordsFoundCount[0] == words.length) return;
        final int foundWordLen = end - start;

        Next:
        for (int i = 0; i < words.length; ++i) {
          final String localName = words[i];
          if (wordsFound[i] || localName.length() != foundWordLen) continue;

          for (int j = 0; j < localName.length(); ++j) {
            if (chars.charAt(start + j) != localName.charAt(j)) continue Next;
          }

          wordsFound[i] = true;
          wordsFoundCount[0]++;
          break;
        }
      }
    };

    final CharSequence contents = tldFileByUri.getViewProvider().getContents();

    IdTableBuilding.scanWords(wordProcessor, contents, 0, contents.length());

    return wordsFoundCount[0] == words.length;
  }

  public interface ExternalUriProcessor {
    void process(@NotNull String uri,@Nullable final String url);
    boolean acceptXmlNs();
    boolean acceptTaglib();
  }
}
