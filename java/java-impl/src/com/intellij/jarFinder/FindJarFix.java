// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarFinder;

import com.intellij.codeInsight.daemon.impl.quickfix.ExpensivePsiIntentionAction;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiQualifiedReferenceElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.components.JBList;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;

/**
 * @author Konstantin Bulenkov
 */
public abstract class FindJarFix extends ExpensivePsiIntentionAction implements IntentionAction, Iconable, LowPriorityAction {
  private static final Logger LOG = Logger.getInstance(FindJarFix.class);

  private static final String CLASS_ROOT_URL = "http://findjar.com/class/";
  private static final String CLASS_PAGE_EXT = ".html";
  private static final String SERVICE_URL = "http://findjar.com";
  private static final @NonNls String FIND_JAR_LAST_USED_DIR_PROPERTY = "findjar.last.used.dir";

  protected final PsiQualifiedReferenceElement myRef;
  protected final Module myModule;
  private final boolean javaLangObjectResolved;
  protected JComponent myEditorComponent;
  private final boolean allFqnsAreUnresolved;

  public FindJarFix(@NotNull PsiQualifiedReferenceElement ref) {
    super(ref.getProject());
    myRef = ref;
    myModule = ModuleUtilCore.findModuleForPsiElement(ref);
    javaLangObjectResolved = JavaPsiFacade.getInstance(myProject).findClass(CommonClassNames.JAVA_LANG_OBJECT, ref.getResolveScope()) != null;
    allFqnsAreUnresolved = allFqnsAreUnresolved(myProject, getPossibleFqns(myRef));
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("quickfix.name.find.jar.on.web");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (isPsiModificationStampChanged()) return false;
    return javaLangObjectResolved && myModule != null && allFqnsAreUnresolved;
  }

  private static boolean allFqnsAreUnresolved(@NotNull Project project, @NotNull List<String> fqns) {
    if (fqns.isEmpty()) return false;
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    return ContainerUtil.all(fqns, fqn -> facade.findClass(fqn, scope) == null);
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, PsiFile file) throws IncorrectOperationException {
    List<String> fqns = getPossibleFqns(myRef);
    myEditorComponent = editor.getComponent();
    if (fqns.size() > 1) {
      JBPopupFactory.getInstance()
        .createPopupChooserBuilder(fqns)
        .setTitle(JavaBundle.message("popup.title.select.qualified.name"))
        .setItemChosenCallback(value -> findJarsForFqn(value, editor))
        .createPopup()
        .showInBestPositionFor(editor);
    }
    else if (fqns.size() == 1) {
      findJarsForFqn(fqns.get(0), editor);
    }
  }

  private void findJarsForFqn(@NlsSafe String fqn, @NotNull Editor editor) {
    ProgressManager.getInstance().run(new Task.Modal(editor.getProject(), JavaBundle.message("progress.title.looking.for.libraries"), true) {
      final Map<String, String> libs = new HashMap<>();

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          String html = HttpRequests.request(CLASS_ROOT_URL + fqn.replace('.', '/') + CLASS_PAGE_EXT).readString(indicator);
          indicator.checkCanceled();
          Matcher matcher = URLUtil.HREF_PATTERN.matcher(html);
          if (!matcher.find()) {
            return;
          }

          do {
            indicator.checkCanceled();

            String pathToJar = matcher.group(1);
            if (pathToJar != null && (pathToJar.startsWith("/jar/") || pathToJar.startsWith("/class/../"))) {
              libs.put(matcher.group(2), SERVICE_URL + pathToJar);
            }
          }
          while (matcher.find());
        }
        catch (IOException e) {
          LOG.warn(e);
        }
      }

      @Override
      public void onSuccess() {
        if (libs.isEmpty()) {
          HintManager.getInstance().showInformationHint(editor, JavaBundle.message("find.jar.hint.text.no.libraries.found.for.fqn", fqn));
        }
        else {
          JBList<@NlsSafe String> libNames = new JBList<>(ContainerUtil.sorted(libs.keySet()));
          libNames.installCellRenderer(o -> new JLabel(o, PlatformIcons.JAR_ICON, SwingConstants.LEFT));
          if (libs.size() == 1) {
            String jarName = libs.keySet().iterator().next();
            String url = libs.get(jarName);
            initiateDownload(url, jarName, editor.getProject());
          }
          else {
            JBPopupFactory.getInstance()
            .createListPopupBuilder(libNames)
            .setTitle(JavaBundle.message("popup.title.select.a.jar.file"))
            .setItemChoosenCallback(() -> {
              String jarName = libNames.getSelectedValue();
              String url = libs.get(jarName);
              if (url != null) {
                initiateDownload(url, jarName, editor.getProject());
              }
            })
            .createPopup().showInBestPositionFor(editor);
          }
        }
      }
    });
  }

  private void initiateDownload(@NotNull String url, @NotNull String jarName, @Nullable Project project) {
    ProgressManager.getInstance().run(new Task.Modal(project, JavaBundle.message("progress.title.download.library.descriptor"), true) {
      private String jarUrl;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          String html = HttpRequests.request(url).readString(indicator);
          indicator.checkCanceled();
          Matcher matcher = URLUtil.HREF_PATTERN.matcher(html);
          if (!matcher.find()) {
            return;
          }

          do {
            indicator.checkCanceled();

            String jarUrl = matcher.group(1);
            if (jarUrl != null && jarUrl.endsWith(jarName)) {
              this.jarUrl = jarUrl;
              return;
            }
          }
          while (matcher.find());
        }
        catch (IOException e) {
          LOG.warn(e);
        }
      }

      @Override
      public void onSuccess() {
        if (jarUrl != null) {
          downloadJar(jarUrl, jarName);
        }
      }
    });
  }

  private void downloadJar(@NotNull String jarUrl, @NotNull String jarName) {
    Project project = myModule.getProject();
    String dirPath = PropertiesComponent.getInstance(project).getValue(FIND_JAR_LAST_USED_DIR_PROPERTY);
    VirtualFile toSelect = dirPath == null ? null : LocalFileSystem.getInstance().findFileByIoFile(new File(dirPath));
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle(
      JavaBundle.message("chooser.title.select.path.to.save.jar"))
      .withDescription(JavaBundle.message("chooser.text.choose.where.to.save.0", jarName));
    VirtualFile file = FileChooser.chooseFile(descriptor, project, toSelect);
    if (file != null) {
      PropertiesComponent.getInstance(project).setValue(FIND_JAR_LAST_USED_DIR_PROPERTY, file.getPath());
      DownloadableFileService downloader = DownloadableFileService.getInstance();
      DownloadableFileDescription description = downloader.createFileDescription(jarUrl, jarName);
      List<VirtualFile> jars =
        downloader.createDownloader(Collections.singletonList(description), jarName)
                  .downloadFilesWithProgress(file.getPath(), project, myEditorComponent);
      if (jars != null && jars.size() == 1) {
        WriteAction.run(() -> OrderEntryFix.addJarToRoots(jars.get(0).getPresentableUrl(), myModule, myRef));
      }
    }
  }

  protected abstract Collection<String> getFqns(@NotNull PsiQualifiedReferenceElement ref);

  private @NotNull List<String> getPossibleFqns(@NotNull PsiQualifiedReferenceElement ref) {
    Collection<String> fqns = getFqns(ref);

    List<String> res = new ArrayList<>(fqns.size());

    for (String fqn : fqns) {
      if (fqn.startsWith("java.") || fqn.startsWith("javax.swing.")) {
        continue;
      }
      int index = fqn.lastIndexOf('.');
      if (index == -1) {
        continue;
      }
      String className = fqn.substring(index + 1);
      if (className.isEmpty() || Character.isLowerCase(className.charAt(0))) {
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
