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
package com.intellij.compiler.impl.javaCompiler.api;

import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.javaCompiler.FileObject;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import org.jetbrains.annotations.NonNls;

import javax.tools.*;
import java.io.File;
import java.net.URI;

/**
 * @author cdr
 */
@SuppressWarnings({"Since15"})
abstract class CompilationEvent {
  protected abstract void process(OutputParser.Callback callback);
  static CompilationEvent progress(final String title, final JavaFileObject fileObject) {
    return new CompilationEvent() {
      @Override
      protected void process(OutputParser.Callback callback) {
        showProgressFor(title, fileObject.toUri(), callback);
      }

      @NonNls
      @Override
      public String toString() {
        return "Progress: "+title+" "+fileObject.toUri();
      }
    };
  }

  private static void showProgressFor(String title, URI uri, OutputParser.Callback callback) {
    callback.setProgressText(title + StringUtil.last(uri.toString(), 70, true));
  }

  static CompilationEvent generateClass(final URI uri, final byte[] bytes) {
    return new CompilationEvent() {
      @Override
      protected void process(OutputParser.Callback callback) {
        showProgressFor("Writing ", uri, callback);
        File file = new File(uri.getPath());
        callback.fileGenerated(new FileObject(file,bytes));
      }
      @NonNls
      @Override
      public String toString() {
        return "Write: "+uri;
      }
    };
  }
  static CompilationEvent diagnostic(final Diagnostic<? extends JavaFileObject> diagnostic) {
    return new CompilationEvent() {
      @Override
      protected void process(OutputParser.Callback callback) {
        JavaFileObject fileObject = diagnostic.getSource();
        String message = diagnostic.getMessage(null);
        String url;
        if (fileObject == null) {
          url = null;
        }
        else {
          URI uri = fileObject.toUri();
          if (uri.getScheme().equals("file")) {
            url = VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(uri.getPath()));
          }
          else {
            url = fileObject.toString();
          }
        }

        CompilerMessageCategory category = diagnostic.getKind() == Diagnostic.Kind.ERROR
                                           ? CompilerMessageCategory.ERROR
                                           : diagnostic.getKind() == Diagnostic.Kind.WARNING ||
                                             diagnostic.getKind() == Diagnostic.Kind.MANDATORY_WARNING
                                             ? CompilerMessageCategory.WARNING
                                             : CompilerMessageCategory.INFORMATION;
        callback.message(category, message, url, (int)diagnostic.getLineNumber(), (int)diagnostic.getColumnNumber());
      }
      @NonNls
      @Override
      public String toString() {
        return "Diagnostic: "+diagnostic;
      }
    };
  }

  public static CompilationEvent fileProcessed() {
    return new CompilationEvent() {
      @Override
      protected void process(OutputParser.Callback callback) {
        callback.fileProcessed(null);
      }
      @NonNls
      @Override
      public String toString() {
        return "Processed";
      }
    };
  }
}
