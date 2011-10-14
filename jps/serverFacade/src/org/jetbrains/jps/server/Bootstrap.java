/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.jps.server;

import com.intellij.compiler.notNullVerification.NotNullVerifyingInstrumenter;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.core.GridConstraints;
import com.jgoodies.forms.layout.CellConstraints;
import gnu.trove.TIntHash;
import net.n3.nanoxml.IXMLBuilder;
import org.codehaus.groovy.GroovyException;
import org.jetbrains.jps.MacroExpander;
import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Bootstrap class for dependency handling
 * @author Eugene Zhuravlev
 *         Date: 9/12/11
 */
public class Bootstrap {
  private Bootstrap() {
  }

  public static List<File> getApplicationClasspath() {
    final List<File> cp = new ArrayList<File>();
    cp.add(getResourcePath(Facade.class));   // server facade module
    cp.add(getResourcePath(TIntHash.class));  // trove
    cp.add(getResourcePath(FileUtil.class));  // util module
    cp.add(getResourcePath(ClassWriter.class));  // asm
    cp.add(getResourcePath(org.objectweb.asm.commons.EmptyVisitor.class));  // asm-commons
    cp.add(getResourcePath(MacroExpander.class));  // jps-model
    cp.add(getResourcePath(AlienFormFileException.class));  // forms-compiler
    cp.add(getResourcePath(GroovyException.class));  // groovy
    cp.add(getResourcePath(org.jdom.input.SAXBuilder.class));  // jdom
    cp.add(getResourcePath(GridConstraints.class));  // forms-rt
    cp.add(getResourcePath(CellConstraints.class));  // jgoodies-forms
    cp.add(getResourcePath(NotNullVerifyingInstrumenter.class));  // not-null
    cp.add(getResourcePath(IXMLBuilder.class));  // nano-xml

    return cp;
  }

  private static File getResourcePath(Class aClass) {
    return new File(PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class"));
  }

}
