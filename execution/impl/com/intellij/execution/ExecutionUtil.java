package com.intellij.execution;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.ide.IconUtilEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;


public class ExecutionUtil {
  private static final Icon INVALID_CONFIGURATION = IconLoader.getIcon("/runConfigurations/invalidConfigurationLayer.png");
  private static final Icon WITH_COVERAGE_CONFIGURATION = IconLoader.getIcon("/runConfigurations/withCoverageLayer.png");

  public static String getRuntimeQualifiedName(final PsiClass aClass) {
    final PsiClass containingClass = aClass.getContainingClass();
    if (containingClass != null) {
      final String parentName = getRuntimeQualifiedName(containingClass);
      return parentName + "$" + aClass.getName();
    }
    else {
      return aClass.getQualifiedName();
    }
  }

  public static String getPresentableClassName(final String rtClassName, final RunConfigurationModule configurationModule) {
    final PsiClass psiClass = configurationModule.findClass(rtClassName);
    if (psiClass != null) {
      return psiClass.getName();
    }
    final int lastDot = rtClassName.lastIndexOf('.');
    if (lastDot == -1 || lastDot == rtClassName.length() - 1) {
      return rtClassName;
    }
    return rtClassName.substring(lastDot + 1, rtClassName.length());
  }

  public static Module findModule(@NotNull final PsiClass psiClass) {
    return ModuleUtil.findModuleForPsiElement(psiClass);
  }

  public static PsiClass findMainClass(final Module module, final String mainClassName) {
    final PsiManager psiManager = PsiManager.getInstance(module.getProject());
    return psiManager.
      findClass(mainClassName.replace('$', '.'),
                GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module));
  }

  public static boolean isNewName(final String name) {

    return name == null || name.startsWith(ExecutionBundle.message("run.configuration.unnamed.name.prefix"));
  }

  public static Location stepIntoSingleClass(final Location location) {
    PsiElement element = location.getPsiElement();
    if (!(element instanceof PsiJavaFile)) {
      if (PsiTreeUtil.getParentOfType(element, PsiClass.class) != null) return location;
      element = PsiTreeUtil.getParentOfType(element, PsiJavaFile.class);
      if (element == null) return location;
    }
    final PsiJavaFile psiFile = (PsiJavaFile)element;
    final PsiClass[] classes = psiFile.getClasses();
    if (classes.length != 1) return location;
    return PsiLocation.fromPsiElement(classes[0]);
  }

  public static String shortenName(final String name, final int toBeAdded) {
    if (name == null) return "";
    final int symbols = Math.max(10, 20 - toBeAdded);
    if (name.length() < symbols) return name;
    else return name.substring(0, symbols) + "...";
  }

  public static String getShortClassName(final String fqName) {
    if (fqName == null) return "";
    final int dotIndex = fqName.lastIndexOf('.');
    if (dotIndex == fqName.length() - 1) return "";
    if (dotIndex < 0) return fqName;
    return fqName.substring(dotIndex + 1, fqName.length());
  }

  public static void showExecutionErrorMessage(final ExecutionException e, final String title, final Project project) {
    if (e instanceof RunCanceledByUserException) {
      return;
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw new RuntimeException(e.getLocalizedMessage());
    }
    final String message = e.getMessage();
    if (message.length() < 100) {
      Messages.showErrorDialog(project, message, title);
      return;
    }
    final DialogBuilder builder = new DialogBuilder(project);
    builder.setTitle(title);
    final JTextArea textArea = new JTextArea();
    textArea.setEditable(false);
    textArea.setForeground(UIUtil.getLabelForeground());
    textArea.setBackground(UIUtil.getLabelBackground());
    textArea.setFont(UIUtil.getLabelFont());
    textArea.setText(message);
    textArea.setWrapStyleWord(false);
    textArea.setLineWrap(true);
    final JScrollPane scrollPane = new JScrollPane(textArea);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    final JPanel panel = new JPanel(new BorderLayout(10, 0));
    panel.setPreferredSize(new Dimension(500, 200));
    panel.add(scrollPane, BorderLayout.CENTER);
    panel.add(new JLabel(Messages.getErrorIcon()), BorderLayout.WEST);
    builder.setCenterPanel(panel);
    builder.setButtonsAlignment(SwingConstants.CENTER);
    builder.addOkAction();
    builder.show();
  }

  public static Icon getConfigurationIcon(final Project project, final RunConfiguration configuration, final boolean invalid) {
    final RunManager runManager = RunManager.getInstance(project);
    final Icon icon = configuration.getFactory().getIcon();
    final Icon configurationIcon = runManager.isTemporary(configuration) ? IconLoader.getTransparentIcon(icon, 0.3f) : icon;
    if (invalid) {
      return IconUtilEx.createLayeredIcon(configurationIcon, INVALID_CONFIGURATION);
    } else if (configuration instanceof CoverageEnabledConfiguration && ((CoverageEnabledConfiguration)configuration).isCoverageEnabled()) {
      return IconUtilEx.createLayeredIcon(configurationIcon, WITH_COVERAGE_CONFIGURATION);
    }
    return configurationIcon;
  }

  public static Icon getConfigurationIcon(final Project project, final RunConfiguration configuration) {
    try {
      configuration.checkConfiguration();
      return getConfigurationIcon(project, configuration, false);
    }
    catch (RuntimeConfigurationException ex) {
      return getConfigurationIcon(project, configuration, true);
    }
  }

  public static boolean isRunnableClass(final PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) return false;
    if (aClass.isInterface()) return false;
    if (!aClass.hasModifierProperty(PsiModifier.PUBLIC)) return false;
    if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) return false;
    if (aClass.getContainingClass() != null && !aClass.hasModifierProperty(PsiModifier.STATIC)) return false;
    return true;
  }
}
