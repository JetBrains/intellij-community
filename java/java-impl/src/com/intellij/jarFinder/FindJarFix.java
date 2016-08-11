package com.intellij.jarFinder;

import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.components.JBList;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NotNullFunction;
import com.intellij.util.PlatformIcons;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import org.cyberneko.html.parsers.DOMParser;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class FindJarFix<T extends PsiElement> implements IntentionAction, Iconable {

  private static final Logger LOG = Logger.getInstance(FindJarFix.class);

  private static final String CLASS_ROOT_URL = "http://findjar.com/class/";
  private static final String CLASS_PAGE_EXT = ".html";
  private static final String SERVICE_URL = "http://findjar.com";
  private static final String LINK_TAG_NAME = "a";
  private static final String LINK_ATTR_NAME = "href";

  protected final T myRef;
  protected final Module myModule;
  protected JComponent myEditorComponent;

  public FindJarFix(T ref) {
    myRef = ref;
    myModule = ModuleUtil.findModuleForPsiElement(ref);
  }

  @NotNull
  @Override
  public String getText() {
    return "Find JAR on web";
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Family name";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myRef.isValid()
           && JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_OBJECT, file.getResolveScope()) != null
           && myModule != null
           && isFqnsOk(project, getPossibleFqns(myRef));
  }

  private static boolean isFqnsOk(Project project, List<String> fqns) {
    if (fqns.isEmpty()) return false;
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    for (String fqn : fqns) {
      if (facade.findClass(fqn, scope) != null) return false;
    }
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final List<String> fqns = getPossibleFqns(myRef);
    myEditorComponent = editor.getComponent();
    if (fqns.size() > 1) {
      final JBList listOfFqns = new JBList(fqns);
      JBPopupFactory.getInstance()
        .createListPopupBuilder(listOfFqns)
        .setTitle("Select Qualified Name")
        .setItemChoosenCallback(() -> {
          final Object value = listOfFqns.getSelectedValue();
          if (value instanceof String) {
            findJarsForFqn(((String)value), editor);
          }
        }).createPopup().showInBestPositionFor(editor);
    }
    else if (fqns.size() == 1) {
      findJarsForFqn(fqns.get(0), editor);
    }
  }

  private void findJarsForFqn(final String fqn, final Editor editor) {
    final Map<String, String> libs = new HashMap<>();

    final Runnable runnable = () -> {
      try {
        final DOMParser parser = new DOMParser();
        parser.parse(CLASS_ROOT_URL + fqn.replace('.', '/') + CLASS_PAGE_EXT);
        final Document doc = parser.getDocument();
        if (doc != null) {
          final NodeList links = doc.getElementsByTagName(LINK_TAG_NAME);
          for (int i = 0; i < links.getLength(); i++) {
            final Node link = links.item(i);
            final String libName = link.getTextContent();
            final NamedNodeMap attributes = link.getAttributes();
            if (attributes != null) {
              final Node href = attributes.getNamedItem(LINK_ATTR_NAME);
              if (href != null) {
                final String pathToJar = href.getTextContent();
                if (pathToJar != null && (pathToJar.startsWith("/jar/") || pathToJar.startsWith("/class/../"))) {
                  libs.put(libName, SERVICE_URL + pathToJar);
                }
              }
            }
          }
        }
      }
      catch (IOException ignore) {//
      }
      catch (Exception e) {//
        LOG.warn(e);
      }
    };

    final Task.Modal task = new Task.Modal(editor.getProject(), "Looking for libraries", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        runUncanceledRunnableWithProgress(runnable, indicator);
      }

      @Override
      public void onSuccess() {
        super.onSuccess();
        if (libs.isEmpty()) {
          HintManager.getInstance().showInformationHint(editor, "No libraries found for '" + fqn + "'");
        } else {
          final ArrayList<String> variants = new ArrayList<>(libs.keySet());
          Collections.sort(variants, (o1, o2) -> o1.compareTo(o2));
          final JBList libNames = new JBList(variants);
          libNames.installCellRenderer(o -> new JLabel(o.toString(), PlatformIcons.JAR_ICON, SwingConstants.LEFT));
          if (libs.size() == 1) {
            final String jarName = libs.keySet().iterator().next();
            final String url = libs.get(jarName);
            initiateDownload(url, jarName);
          } else {
            JBPopupFactory.getInstance()
            .createListPopupBuilder(libNames)
            .setTitle("Select a JAR file")
            .setItemChoosenCallback(() -> {
              final Object value = libNames.getSelectedValue();
              if (value instanceof String) {
                final String jarName = (String)value;
                final String url = libs.get(jarName);
                if (url != null) {
                  initiateDownload(url, jarName);
                }
              }
            })
            .createPopup().showInBestPositionFor(editor);
          }
        }
      }
    };

    ProgressManager.getInstance().run(task);
  }

  private void initiateDownload(String url, String jarName) {
    DOMParser parser = new DOMParser();
    try {
      parser.parse(url);
      final Document doc = parser.getDocument();
      if (doc != null) {
        final NodeList links = doc.getElementsByTagName(LINK_TAG_NAME);
        if (links != null) {
          for (int i = 0; i < links.getLength(); i++) {
            final Node item = links.item(i);
            if (item != null) {
              final NamedNodeMap attributes = item.getAttributes();
              if (attributes != null) {
                final Node link = attributes.getNamedItem(LINK_ATTR_NAME);
                if (link != null) {
                  final String jarUrl = link.getTextContent();
                  if (jarUrl != null && jarUrl.endsWith(jarName)) {
                    downloadJar(jarUrl, jarName);
                  }
                }
              }
            }
          }
        }
      }
    }
    catch (SAXException e) {//
    }
    catch (IOException e) {//
    }
  }

  private static void runUncanceledRunnableWithProgress(Runnable run, ProgressIndicator indicator) {
    Thread t = new Thread(run, "FindJar download thread");
    t.setDaemon(true);
    t.start();

    try {
      while (t.isAlive()) {
        t.join(500);
        indicator.checkCanceled();
      }
    }
    catch (InterruptedException e) {
      indicator.checkCanceled();
    }
  }

  private void downloadJar(String jarUrl, String jarName) {
    final Project project = myModule.getProject();
    final String dirPath = PropertiesComponent.getInstance(project).getValue("findjar.last.used.dir");
    VirtualFile toSelect = dirPath == null ? null : LocalFileSystem.getInstance().findFileByIoFile(new File(dirPath));
    final VirtualFile file = FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFolderDescriptor(), project, toSelect);
    if (file != null) {
      PropertiesComponent.getInstance(project).setValue("findjar.last.used.dir", file.getPath());
      final DownloadableFileService downloader = DownloadableFileService.getInstance();
      final DownloadableFileDescription description = downloader.createFileDescription(jarUrl, jarName);
      final List<VirtualFile> jars =
        downloader.createDownloader(Arrays.asList(description), jarName)
                  .downloadFilesWithProgress(file.getPath(), project, myEditorComponent);
      if (jars != null && jars.size() == 1) {
        AccessToken token = WriteAction.start();
        try {
          OrderEntryFix.addJarToRoots(jars.get(0).getPresentableUrl(), myModule, myRef);
        }
        finally {
          token.finish();
        }
      }
    }
  }

  protected abstract Collection<String> getFqns(@NotNull T ref);
  
  protected List<String> getPossibleFqns(T ref) {
    Collection<String> fqns = getFqns(ref);
    
    List<String> res = new ArrayList<>(fqns.size());

    for (String fqn : fqns) {
      if (fqn.startsWith("java.") || fqn.startsWith("javax.swing.")) {
        continue;
      }
      final int index = fqn.lastIndexOf('.');
      if (index == -1) {
        continue;
      }
      final String className = fqn.substring(index + 1);
      if (className.length() == 0 || Character.isLowerCase(className.charAt(0))) {
        continue;
      }

      res.add(fqn);
    }

    return res;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public Icon getIcon(int flags) {
    return PlatformIcons.WEB_ICON;
  }
}
