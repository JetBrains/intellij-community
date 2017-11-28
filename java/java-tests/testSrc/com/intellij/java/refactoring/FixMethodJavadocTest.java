/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.refactoring;

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
  public String transform(String testName, String[] data) {
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
