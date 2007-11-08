/*
 * User: anna
 * Date: 25-Sep-2007
 */
package com.intellij.openapi.project.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class ProjectMacrosUtil {
  private static final Logger LOG = Logger.getInstance("#" + ProjectMacrosUtil.class.getName());

  private ProjectMacrosUtil() {
  }

  public static boolean showMacrosConfigurationDialog(Project project, final Set<String> undefinedMacros) {
    final String text = ProjectBundle.message("project.load.undefined.path.variables.message");
    final Application application = ApplicationManager.getApplication();
    if (application.isHeadlessEnvironment() || application.isUnitTestMode()) {
      throw new RuntimeException(text + ": " + StringUtil.join(undefinedMacros, ", "));
    }
    final UndefinedMacrosConfigurable configurable =
      new UndefinedMacrosConfigurable(text, undefinedMacros.toArray(new String[undefinedMacros.size()]));
    final SingleConfigurableEditor editor = new SingleConfigurableEditor(project, configurable) {
      protected void doOKAction() {
        if (!getConfigurable().isModified()) {
          Messages.showErrorDialog(getContentPane(), ProjectBundle.message("project.load.undefined.path.variables.all.needed"),
                                   ProjectBundle.message("project.load.undefined.path.variables.title"));
          return;
        }
        super.doOKAction();
      }
    };
    editor.show();
    return editor.isOK();
  }

  public static boolean checkMacros(final Project project, final Set<String> usedMacros) {
    usedMacros.removeAll(getDefinedMacros());

    // try to lookup values in System properties
    @NonNls final String pathMacroSystemPrefix = "path.macro.";
    for (Iterator it = usedMacros.iterator(); it.hasNext();) {
      final String macro = (String)it.next();
      final String value = System.getProperty(pathMacroSystemPrefix + macro, null);
      if (value != null) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            PathMacros.getInstance().setMacro(macro, value);
          }
        });
        it.remove();
      }
    }

    if (usedMacros.isEmpty()) {
      return true; // all macros in configuration files are defined
    }

    // there are undefined macros, need to define them before loading components
    final boolean[] result = new boolean[1];

    try {
      final Runnable r = new Runnable() {
        public void run() {
          result[0] = showMacrosConfigurationDialog(project, usedMacros);
        }
      };

      if (!ApplicationManager.getApplication().isDispatchThread()) {
        SwingUtilities.invokeAndWait(r);
      }
      else {
        r.run();
      }
    }
    catch (InterruptedException e) {
      LOG.error(e);
    }
    catch (InvocationTargetException e) {
      LOG.error(e);
    }
    return result[0];
  }

  public static Set<String> getDefinedMacros() {
    final PathMacros pathMacros = PathMacros.getInstance();

    Set<String> definedMacros = new HashSet<String>(pathMacros.getUserMacroNames());
    definedMacros.addAll(pathMacros.getSystemMacroNames());
    definedMacros = Collections.unmodifiableSet(definedMacros);
    return definedMacros;
  }
}