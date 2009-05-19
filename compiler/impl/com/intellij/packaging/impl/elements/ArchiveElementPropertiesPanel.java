package com.intellij.packaging.impl.elements;

import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingElementPropertiesPanel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author nik
 */
public class ArchiveElementPropertiesPanel extends PackagingElementPropertiesPanel<ArchivePackagingElement> {
  private JPanel myMainPanel;
  private TextFieldWithBrowseButton myMainClassField;
  private TextFieldWithBrowseButton myClasspathField;
  private JLabel myTitleLabel;

  public ArchiveElementPropertiesPanel(final PackagingEditorContext context) {
    myMainClassField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Project project = context.getProject();
        final TreeClassChooserFactory chooserFactory = TreeClassChooserFactory.getInstance(project);
        final GlobalSearchScope searchScope = GlobalSearchScope.allScope(project);
        final PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(myMainClassField.getText(), searchScope);
        final TreeClassChooser chooser = chooserFactory.createWithInnerClassesScopeChooser("Select Main Class", searchScope, new MainClassFilter(), aClass);
        chooser.showDialog();
        final PsiClass selected = chooser.getSelectedClass();
        if (selected != null) {
          myMainClassField.setText(selected.getQualifiedName());
        }
      }
    });
  }

  @NotNull
  public JComponent getComponent() {
    return myMainPanel;
  }

  public void loadFrom(@NotNull ArchivePackagingElement element) {
    myTitleLabel.setText("'" + element.getArchiveFileName() + "' manifest properties:");
    myMainClassField.setText(element.getMainClass());
    myClasspathField.setText(element.getClasspath());
  }

  public void saveTo(@NotNull ArchivePackagingElement element) {
    element.setMainClass(myMainClassField.getText());
    element.setClasspath(myClasspathField.getText());
  }

  private static class MainClassFilter implements TreeClassChooser.ClassFilter {
    public boolean isAccepted(PsiClass aClass) {
      return PsiMethodUtil.MAIN_CLASS.value(aClass) && PsiMethodUtil.hasMainMethod(aClass);
    }
  }
}
