/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.JavaSourceTransformingCompiler;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.io.*;

/**          
 * @author Eugene Zhuravlev
 *         Date: Jul 10, 2004
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class DummyTransformingCompiler implements JavaSourceTransformingCompiler{
  public boolean isTransformable(VirtualFile file) {
    return "A.java".equals(file.getName());
  }

  public boolean transform(CompileContext context, final VirtualFile file, VirtualFile originalFile) {
    System.out.println("DummyTransformingCompiler.transform");
    final String url = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return file.getPresentableUrl();
      }
    });
    context.getProgressIndicator().setText("Transforming file: " + url);
    try {
      OutputStream os = new BufferedOutputStream(new FileOutputStream(new File(url)));
      DataOutput out = new DataOutputStream(os);
      out.writeBytes("package a; ");
      out.writeBytes("public class A { public static void main(String[] args) { System.out.println(\"Hello from modified class\");} }");
      os.close();
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        public void run() {
          file.refresh(false, false);
        }
      });
      return true;
    }
    catch (FileNotFoundException e) {
      context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
    }
    catch (IOException e) {
      context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
    }
    return false;
  }

  @NotNull
  public String getDescription() {
    return "a dummy compiler for testing";
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }
}
