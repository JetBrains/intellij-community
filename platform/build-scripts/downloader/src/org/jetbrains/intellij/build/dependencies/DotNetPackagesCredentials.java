// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.jetbrains.intellij.build.dependencies.BuildDependenciesLogging.error;
import static org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.*;

@SuppressWarnings("unused")
public final class DotNetPackagesCredentials {
  public static boolean setupSystemCredentials() {
    try {
      if (loadFromEnvVars()) {
        return true;
      }
      if (loadFromNuGetConfig()) {
        return true;
      }
    }
    catch (Throwable t) {
      StringWriter writer = new StringWriter();
      t.printStackTrace(new PrintWriter(writer));
      error(writer.getBuffer().toString());
    }
    return false;
  }

  private static boolean loadFromEnvVars() {
    String credentialsFromEnvVar = System.getenv("NuGetPackageSourceCredentials_dotnet_build_space");
    if (credentialsFromEnvVar == null) {
      return false;
    }
    String[] parts = credentialsFromEnvVar.split(";");
    boolean isUsernameSet = false;
    boolean isPasswordSet = false;
    for (String part : parts) {
      String[] subParts = part.split("=");
      if ("Username".equals(subParts[0])) {
        System.setProperty(BuildDependenciesConstants.JPS_AUTH_SPACE_USERNAME, subParts[1]);
        isUsernameSet = true;
      }
      else if ("Password".equals(subParts[0])) {
        System.setProperty(BuildDependenciesConstants.JPS_AUTH_SPACE_PASSWORD, subParts[1]);
        isPasswordSet = true;
      }
    }
    return isUsernameSet && isPasswordSet;
  }

  private static boolean loadFromNuGetConfig() throws IOException, SAXException {
    File nuGetConfig;
    if (isWindows) {
      nuGetConfig = Path.of(System.getenv("APPDATA"), "NuGet", "NuGet.Config").toFile();
    }
    else {
      nuGetConfig = Path.of(System.getProperty("user.home"), ".nuget", "NuGet", "NuGet.Config").toFile();
    }
    if (!nuGetConfig.exists()) {
      return false;
    }
    DocumentBuilder documentBuilder = createDocumentBuilder();
    Document document = documentBuilder.parse(nuGetConfig);
    Element packageSourceCredentialsElement = tryGetSingleChildElement(document.getDocumentElement(), "packageSourceCredentials");
    if (packageSourceCredentialsElement == null) {
      return false;
    }
    Element dotNetSpaceBuild = tryGetSingleChildElement(packageSourceCredentialsElement, "dotnet_build_space");
    if (dotNetSpaceBuild == null) {
      return false;
    }
    boolean isUsernameSet = false;
    boolean isPasswordSet = false;
    for (int i = 0; i < dotNetSpaceBuild.getChildNodes().getLength(); i++) {
      if (dotNetSpaceBuild.getChildNodes().item(i) instanceof Element) {
        Element childElement = (Element) dotNetSpaceBuild.getChildNodes().item(i);
        if ("add".equals(childElement.getTagName())) {
          String key = childElement.getAttribute("key");
          String value = childElement.getAttribute("value");
          if ("Username".equals(key)) {
            System.setProperty(BuildDependenciesConstants.JPS_AUTH_SPACE_USERNAME, value);
            isUsernameSet = true;
          }
          else if ("ClearTextPassword".equals(key)) {
            System.setProperty(BuildDependenciesConstants.JPS_AUTH_SPACE_PASSWORD, value);
            isPasswordSet = true;
          }
        }
      }
    }
    return isUsernameSet && isPasswordSet;
  }
}
