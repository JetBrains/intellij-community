package com.intellij.refactoring;

import com.intellij.FileSetTestCase;
import com.intellij.psi.*;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.containers.HashSet;
import junit.framework.Test;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.Set;

/**
 *  @author dsl
 */
public abstract class FixMethodJavadocTest extends FileSetTestCase {
  FixMethodJavadocTest() {
    super(findPath());
  }

  private static String findPath() {
    final URL res = FixMethodJavadocTest.class.getResource("/" + FixMethodJavadocTest.class.getName().replace('.', '/') + ".class");
    File f = new File(res.getFile());
    String result = f.getParent() + File.separatorChar + "methodJavaDocData";
    result = result.replaceAll("classes", "");
    return result;
  }

  @Override
  public String transform(String testName, String[] data) throws Exception {
    final PsiManager manager = PsiManager.getInstance(myProject);
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    final PsiMethod method = factory.createMethodFromText(data[0], null);
    final HashSet<PsiParameter> newParameters = new HashSet<>();
    if (data.length == 2) {
      final String[] strings = data[1].split("\\s+");
      collectNewParameters(method, strings, newParameters);
    }
    RefactoringUtil.fixJavadocsForParams(method, newParameters);
    return method.getText();
  }

  private void collectNewParameters(PsiMethod method, String[] names, Set<PsiParameter> newParameters) {
    Set<String> newNames = new HashSet<>(Arrays.asList(names));
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      if (newNames.contains(parameter.getName())) {
        newParameters.add(parameter);
      }
    }
  }

  public static Test suite() {
    return new FixMethodJavadocTest(){};
  }
}
