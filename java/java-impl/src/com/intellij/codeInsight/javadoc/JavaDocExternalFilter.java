/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.javadoc;

import com.intellij.codeInsight.documentation.AbstractExternalFilter;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.documentation.DocumentationManagerProtocol;
import com.intellij.codeInsight.documentation.PlatformDocumentationUtil;
import com.intellij.ide.BrowserUtil;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.builtInWebServer.BuiltInServerOptions;
import org.jetbrains.builtInWebServer.WebServerPathToFileManager;

import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: May 2, 2003
 * Time: 8:35:34 PM
 * To change this template use Options | File Templates.
 */

public class JavaDocExternalFilter extends AbstractExternalFilter {
  private final Project myProject;
  
  private static final Trinity<Pattern, Pattern, Boolean> ourPackageInfoSettings = Trinity.create(
    Pattern.compile("package\\s+[^\\s]+\\s+description", Pattern.CASE_INSENSITIVE),
    Pattern.compile("START OF BOTTOM NAVBAR", Pattern.CASE_INSENSITIVE),
    Boolean.TRUE
  );
  
  protected static @NonNls final Pattern ourHTMLsuffix = Pattern.compile("[.][hH][tT][mM][lL]?");
  protected static @NonNls final Pattern ourParentFolderprefix = Pattern.compile("^[.][.]/");
  protected static @NonNls final Pattern ourAnchorsuffix = Pattern.compile("#(.*)$");
  protected static @NonNls final Pattern ourHTMLFilesuffix = Pattern.compile("/([^/]*[.][hH][tT][mM][lL]?)$");
  private static @NonNls final Pattern ourHREFselector = Pattern.compile("<A.*?HREF=\"([^>\"]*)\"", Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
  private static @NonNls final Pattern ourMethodHeading = Pattern.compile("<H[34]>(.+?)</H[34]>", Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
  protected static @NonNls final String DOC_ELEMENT_PROTOCOL = "doc_element://";
  @NonNls protected static final String H2 = "</H2>";
  @NonNls protected static final String HTML_CLOSE = "</HTML>";
  @NonNls protected static final String HTML = "<HTML>";

  private final RefConvertor[] myReferenceConvertors = new RefConvertor[]{
    new RefConvertor(ourHREFselector) {
      @Override
      protected String convertReference(String root, String href) {
        if (BrowserUtil.isAbsoluteURL(href)) {
          return href;
        }

        if (StringUtil.startsWithChar(href, '#')) {
          return DOC_ELEMENT_PROTOCOL + root + href;
        }

        String nakedRoot = ourHTMLFilesuffix.matcher(root).replaceAll("/");

        String stripped = ourHTMLsuffix.matcher(href).replaceAll("");
        int len = stripped.length();

        do stripped = ourParentFolderprefix.matcher(stripped).replaceAll(""); while (len > (len = stripped.length()));

        final String elementRef = stripped.replaceAll("/", ".");
        final String classRef = ourAnchorsuffix.matcher(elementRef).replaceAll("");

        return
          (JavaPsiFacade.getInstance(myProject).findClass(classRef, GlobalSearchScope.allScope(myProject)) != null)
          ? DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL + elementRef
          : DOC_ELEMENT_PROTOCOL + doAnnihilate(nakedRoot + href);
      }
    }
  };

  public JavaDocExternalFilter(Project project) {
    myProject = project;
  }

  @Override
  protected RefConvertor[] getRefConverters() {
    return myReferenceConvertors;
  }

  @Nullable
  public static String filterInternalDocInfo(String text) {
    if (text == null) {
      return null;
    }
    text = PlatformDocumentationUtil.fixupText(text);
    return text;
  }

  @Override
  @Nullable
   public String getExternalDocInfoForElement(@NotNull String docURL, final PsiElement element) throws Exception {
    CharSequence externalDoc = null;
    String builtInServer = "http://localhost:" + BuiltInServerOptions.getInstance().getEffectiveBuiltInServerPort() + "/" + myProject.getName() + "/";
    if (docURL.startsWith(builtInServer)) {
      VirtualFile file = WebServerPathToFileManager.getInstance(myProject).get(docURL.substring(builtInServer.length()));
      if (file != null) {
        InputStreamReader reader = new InputStreamReader(file.getInputStream(), CharsetToolkit.UTF8_CHARSET);
        StringBuilder result = new StringBuilder();
        try {
          doBuildFromStream(docURL, reader, result);
        }
        finally {
          reader.close();
        }

        externalDoc = result;
      }
    }

    if (externalDoc == null) {
      externalDoc = super.getExternalDocInfoForElement(docURL, element);
    }

    if (externalDoc == null) {
      return null;
    }

    if (element instanceof PsiMethod) {
      final String className = ApplicationManager.getApplication().runReadAction(
        new NullableComputable<String>() {
          @Override
          @Nullable
          public String compute() {
            PsiClass aClass = ((PsiMethod)element).getContainingClass();
            return aClass == null ? null : aClass.getQualifiedName();
          }
        }
      );
      Matcher matcher = ourMethodHeading.matcher(externalDoc);
      StringBuilder buffer = new StringBuilder("<h3>");
      DocumentationManager.createHyperlink(buffer, className, className, false);
      return matcher.replaceFirst(buffer.append("</h3>").toString());
    }
    return externalDoc.toString();
  }

  @NotNull
  @Override
  protected Trinity<Pattern, Pattern, Boolean> getParseSettings(@NotNull String url) {
    return url.endsWith(JavaDocumentationProvider.PACKAGE_SUMMARY_FILE) ? ourPackageInfoSettings : super.getParseSettings(url);
  }
}
