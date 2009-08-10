package com.intellij.packaging.impl.ui.properties;

import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.impl.elements.ArchivePackagingElement;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author nik
 */
public class ArchiveElementPropertiesPanel extends ElementWithClasspathPropertiesPanel<ArchivePackagingElement> {
  private JPanel myMainPanel;
  private TextFieldWithBrowseButton myMainClassField;
  private TextFieldWithBrowseButton myClasspathField;
  private JLabel myTitleLabel;

  public ArchiveElementPropertiesPanel(final ArtifactEditorContext context) {
    super(context);
    myMainClassField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Project project = context.getProject();
        final TreeClassChooserFactory chooserFactory = TreeClassChooserFactory.getInstance(project);
        final GlobalSearchScope searchScope = GlobalSearchScope.allScope(project);
        final PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(myMainClassField.getText(), searchScope);
        final TreeClassChooser chooser =
            chooserFactory.createWithInnerClassesScopeChooser("Select Main Class", searchScope, new MainClassFilter(), aClass);
        chooser.showDialog();
        final PsiClass selected = chooser.getSelectedClass();
        if (selected != null) {
          myMainClassField.setText(selected.getQualifiedName());
        }
      }
    });
    initClasspathField();
  }

  @NotNull
  public JComponent getComponent() {
    return myMainPanel;
  }

  @Override
  public boolean isAvailable(@NotNull ArchivePackagingElement element) {
    final String name = element.getArchiveFileName();
    return name.length() >= 4 && name.charAt(name.length() - 4) == '.' && StringUtil.endsWithIgnoreCase(name, "ar");
  }

  @Override
  protected TextFieldWithBrowseButton getClasspathField() {
    return myClasspathField;
  }

  public void loadFrom(@NotNull ArchivePackagingElement element) {
    myTitleLabel.setText("'" + element.getArchiveFileName() + "' manifest properties:");
    myMainClassField.setText(element.getMainClass());
    super.loadFrom(element);
  }

  @Override
  public boolean isModified(@NotNull ArchivePackagingElement original) {
    return super.isModified(original) || !Comparing.equal(original.getMainClass(), getConfiguredMainClass());
  }

  public void saveTo(@NotNull ArchivePackagingElement element) {
    element.setMainClass(getConfiguredMainClass());
    super.saveTo(element);
  }

  @Nullable
  private String getConfiguredMainClass() {
    final String className = myMainClassField.getText();
    return className.length() != 0 ? className : null;
  }

  private static class MainClassFilter implements TreeClassChooser.ClassFilter {
    public boolean isAccepted(PsiClass aClass) {
      return PsiMethodUtil.MAIN_CLASS.value(aClass) && PsiMethodUtil.hasMainMethod(aClass);
    }
  }
}
