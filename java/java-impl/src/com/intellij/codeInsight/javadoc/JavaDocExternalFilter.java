// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.javadoc;

import com.intellij.codeInsight.documentation.AbstractExternalFilter;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.documentation.PlatformDocumentationUtil;
import com.intellij.ide.BrowserUtil;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.builtInWebServer.BuiltInServerOptions;
import org.jetbrains.builtInWebServer.WebServerPathToFileManager;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.Pair.pair;

public class JavaDocExternalFilter extends AbstractExternalFilter {
  private final Project myProject;
  private PsiElement myElement;

  private static final ParseSettings ourPackageInfoSettings = new ParseSettings(
    Pattern.compile("package\\s+[^\\s]+\\s+description", Pattern.CASE_INSENSITIVE),
    Pattern.compile("START OF BOTTOM NAVBAR", Pattern.CASE_INSENSITIVE),
    true, false
  );

  private static final Pattern HREF_SELECTOR = Pattern.compile("<A.*?HREF=\"([^>\"]*)\"", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern METHOD_HEADING = Pattern.compile("<H[34]>(.+?)</H[34]>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  private final RefConvertor[] myReferenceConverters = {
    new RefConvertor(HREF_SELECTOR) {
      @Override
      protected String convertReference(String root, String href) {
        if (BrowserUtil.isAbsoluteURL(href)) {
          return href;
        }
        String reference = JavaDocInfoGenerator.createReferenceForRelativeLink(href, myElement);
        if (reference == null) {
          if (href.startsWith("#")) {
            return root + href;
          }
          else {
            String nakedRoot = ourHtmlFileSuffix.matcher(root).replaceAll("/");
            return doAnnihilate(nakedRoot + href);
          }
        }
        else {
          return reference;
        }
      }
    }
  };

  public JavaDocExternalFilter(Project project) {
    myProject = project;
  }

  @Override
  protected RefConvertor[] getRefConverters() {
    return myReferenceConverters;
  }

  @Nullable
  public static String filterInternalDocInfo(String text) {
    return text == null ? null : PlatformDocumentationUtil.fixupText(text);
  }

  @Nullable
  @Override
  public String getExternalDocInfoForElement(@NotNull String docURL, PsiElement element) throws Exception {
    String externalDoc = null;
    myElement = element;

    String projectPath = "/" + myProject.getName() + "/";
    String builtInServer = "http://localhost:" + BuiltInServerOptions.getInstance().getEffectiveBuiltInServerPort() + projectPath;
    if (docURL.startsWith(builtInServer)) {
      Url url = Urls.parseFromIdea(docURL);
      if (url != null) {
        VirtualFile file = WebServerPathToFileManager.getInstance(myProject).findVirtualFile(url.getPath().substring(projectPath.length()));
        if (file != null) {
          StringBuilder result = new StringBuilder();
          try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            doBuildFromStream(docURL, reader, result);
          }
          externalDoc = correctDocText(docURL, result);
        }
      }
    }

    if (externalDoc == null) {
      externalDoc = super.getExternalDocInfoForElement(docURL, element);
    }

    if (externalDoc == null) {
      return null;
    }

    if (element instanceof PsiMethod) {
      Pair<String, String> classNameAndPresentation = ReadAction.compute(
        () -> {
          PsiClass aClass = ((PsiMethod)element).getContainingClass();
          if (aClass != null) {
            String qName = aClass.getQualifiedName();
            return pair(qName, qName + JavaDocInfoGenerator.generateTypeParameters(aClass, true));
          }
          return null;
        }
      );
      if (classNameAndPresentation == null) return externalDoc;
      Matcher matcher = METHOD_HEADING.matcher(externalDoc);
      StringBuilder buffer = new StringBuilder("<h3>");
      DocumentationManager.createHyperlink(buffer, classNameAndPresentation.first, classNameAndPresentation.second, false);
      return matcher.replaceFirst(buffer.append("</h3>").toString());
    }

    return externalDoc;
  }

  @NotNull
  @Override
  protected ParseSettings getParseSettings(@NotNull String url) {
    return url.endsWith(JavaDocumentationProvider.PACKAGE_SUMMARY_FILE) ? ourPackageInfoSettings : super.getParseSettings(url);
  }
}