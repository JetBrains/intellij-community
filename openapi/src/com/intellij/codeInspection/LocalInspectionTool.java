/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInspection;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import org.jdom.Element;

import javax.swing.*;

/**
 * Implement this abstract class in order to provide new inspection tool functionality. The major API limitation here is
 * subclasses should be stateless. Thus <code>check&lt;XXX&gt;</code> methods will be called in no particular order and
 * instances of this class provided by {@link InspectionToolProvider#getInspectionClasses()} will be created on demand.
 * The other important thing is problem anchors (PsiElements) reported by <code>check&lt;XXX&gt;</code> methods should
 * lie under corresponding first parameter of one method.
 */
public abstract class LocalInspectionTool {
  /**
   * Override this to report problems at method level.
   *
   * @param method     to check.
   * @param manager    InspectionManager to ask for ProblemDescriptor's from.
   * @param isOnTheFly true if called during on the fly editor highlighting. Called from Inspect Code action otherwise.
   * @return <code>null</code> if no problems found or not applicable at method level.
   */
  public ProblemDescriptor[] checkMethod(PsiMethod method, InspectionManager manager, boolean isOnTheFly) {
    return null;
  }

  /**
   * Override this to report problems at class level.
   *
   * @param aClass     to check.
   * @param manager    InspectionManager to ask for ProblemDescriptor's from.
   * @param isOnTheFly true if called during on the fly editor highlighting. Called from Inspect Code action otherwise.
   * @return <code>null</code> if no problems found or not applicable at class level.
   */
  public ProblemDescriptor[] checkClass(PsiClass aClass, InspectionManager manager, boolean isOnTheFly) {
    return null;
  }

  /**
   * Override this to report problems at field level.
   *
   * @param field      to check.
   * @param manager    InspectionManager to ask for ProblemDescriptor's from.
   * @param isOnTheFly true if called during on the fly editor highlighting. Called from Inspect Code action otherwise.
   * @return <code>null</code> if no problems found or not applicable at field level.
   */
  public ProblemDescriptor[] checkField(PsiField field, InspectionManager manager, boolean isOnTheFly) {
    return null;
  }

  public abstract String getGroupDisplayName();

  public abstract String getDisplayName();

  /**
   * @return short name that is used in two cases: \inspectionDescriptions\&lt;short_name&gt;.html resource may contain short inspection
   *         description to be shown in "Inspect Code..." dialog and also provide some file name convention when using offline
   *         inspection or export to HTML function. Should be unique among all inspections.
   */
  public abstract String getShortName();

  /**
   * @return highlighting level for this inspection tool that is used in default settings
   */
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  public boolean isEnabledByDefault() {
    return false;
  }

  /**
   * @return null if no UI options required
   */
  public JComponent createOptionsPanel() {
    return null;
  }

  /**
   * @return descriptive name to be used in "suppress" comments and annotations,
   *         must consist of [a-zA-Z_0-9]+
   */
  public String getID() {
    return getShortName();
  }

  /**
   * Read in settings from xml config. Default implementation uses DefaultJDOMExternalizer so you may use public fields like <code>int TOOL_OPTION</code> to store your options.
   *
   * @param node to read settings from.
   * @throws InvalidDataException
   */
  public void readSettings(Element node) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, node);
  }

  /**
   * Store current settings in xml config. Default implementation uses DefaultJDOMExternalizer so you may use public fields like <code>int TOOL_OPTION</code> to store your options.
   *
   * @param node to store settings to.
   * @throws WriteExternalException
   */
  public void writeSettings(Element node) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, node);
  }
}
