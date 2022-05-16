// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl;

import com.intellij.openapi.util.Pair;
import de.pdark.decentxml.*;
import groovy.lang.Closure;
import io.opentelemetry.api.trace.Span;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.intellij.build.BuildContext;
import org.jetbrains.intellij.build.CompatibleBuildRange;
import org.jetbrains.intellij.build.ProductModulesLayout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class PluginXmlPatcher {
  public PluginXmlPatcher(String releaseDate, String releaseVersion) {
    myReleaseDate = releaseDate;
    myReleaseVersion = releaseVersion;
  }

  public static Pair<String, String> getCompatiblePlatformVersionRange(CompatibleBuildRange compatibleBuildRange, String buildNumber) {
    String sinceBuild;
    String untilBuild;
    if (!compatibleBuildRange.equals(CompatibleBuildRange.EXACT) && buildNumber.matches( / (\d +\.)+\d +/)){
      if (compatibleBuildRange.equals(CompatibleBuildRange.ANY_WITH_SAME_BASELINE)) {
        sinceBuild = buildNumber.substring(0, buildNumber.indexOf("."));
        untilBuild = buildNumber.substring(0, buildNumber.indexOf(".")) + ".*";
      }
      else {
        if (buildNumber.matches( /\d +\.\d +/)){
          sinceBuild = buildNumber;
        }
 else{
          sinceBuild = buildNumber.substring(0, buildNumber.lastIndexOf("."));
        }

        int end = compatibleBuildRange.equals(CompatibleBuildRange.RESTRICTED_TO_SAME_RELEASE)
                  ? buildNumber.lastIndexOf(".")
                  : buildNumber.indexOf(".");
        untilBuild = buildNumber.substring(0, end) + ".*";
      }
    }
 else{
      sinceBuild = buildNumber;
      untilBuild = buildNumber;
    }

    return new Pair<String, String>(sinceBuild, untilBuild);
  }

  public static void patchPluginXml(ModuleOutputPatcher moduleOutputPatcher,
                                    PluginLayout plugin,
                                    Set<PluginLayout> pluginsToPublish,
                                    PluginXmlPatcher pluginXmlPatcher,
                                    BuildContext context) {
    boolean bundled = !pluginsToPublish.contains(plugin);
    Path moduleOutput = context.getModuleOutputDir(context.findRequiredModule(plugin.getMainModule()));
    Path pluginXmlPath = moduleOutput.resolve("META-INF/plugin.xml");
    if (Files.notExists(pluginXmlPath)) {
      context.getMessages().error("plugin.xml not found in " + plugin.getMainModule() + " module: " + String.valueOf(pluginXmlPath));
    }


    ProductModulesLayout productLayout = context.getProductProperties().getProductLayout();
    Boolean includeInBuiltinCustomRepository = productLayout.getPrepareCustomPluginRepositoryForPublishedPlugins() &&
                                               context.getProprietaryBuildTools().getArtifactsServer() != null;
    CompatibleBuildRange compatibleBuildRange = bundled || plugin.getPluginCompatibilityExactVersion() || includeInBuiltinCustomRepository
                                                ? CompatibleBuildRange.EXACT
                                                : context.getApplicationInfo().isEAP()
                                                  ? CompatibleBuildRange.RESTRICTED_TO_SAME_RELEASE
                                                  : CompatibleBuildRange.NEWER_WITH_SAME_BASELINE;

    String defaultPluginVersion = context.getBuildNumber().endsWith(".SNAPSHOT")
                                  ? context.getBuildNumber() +
                                    "." +
                                    PluginXmlPatcher.getPluginDateFormat().format(ZonedDateTime.now())
                                  : context.getBuildNumber();

    String pluginVersion = plugin.getVersionEvaluator().evaluate(pluginXmlPath, defaultPluginVersion, context);

    Pair<String, String> sinceUntil = getCompatiblePlatformVersionRange(compatibleBuildRange, context.getBuildNumber());
    String content;
    try {
      content =
        pluginXmlPatcher.patchPluginXml(pluginXmlPath, plugin.getMainModule(), pluginVersion, sinceUntil, pluginsToPublish.contains(plugin),
                                        plugin.getRetainProductDescriptorForBundledPlugin(), context.getApplicationInfo().isEAP(),
                                        context.getApplicationInfo().getProductName());
      content = plugin.getPluginXmlPatcher().apply(content);
    }
    catch (Throwable e) {
      throw new RuntimeException("Could not patch " + String.valueOf(pluginXmlPath), e);
    }


    moduleOutputPatcher.patchModuleOutput(plugin.getMainModule(), "META-INF/plugin.xml", content);
  }

  public String patchPluginXml(@NotNull Path pluginXmlFile,
                               String pluginModuleName,
                               String pluginVersion,
                               Pair<String, String> compatibleSinceUntil,
                               final boolean toPublish,
                               boolean retainProductDescriptorForBundledPlugin,
                               boolean isEap,
                               String productName) {
    Document doc = XMLParser.parse(Files.readString(pluginXmlFile));

    Element ideaVersionElement = getOrCreateTopElement(doc, "idea-version", new ArrayList<String>(Arrays.asList("id", "name")));
    ideaVersionElement.setAttribute("since-build", compatibleSinceUntil.getFirst());
    ideaVersionElement.setAttribute("until-build", compatibleSinceUntil.getSecond());

    Element versionElement = getOrCreateTopElement(doc, "version", new ArrayList<String>(Arrays.asList("id", "name")));
    versionElement.setText(pluginVersion);

    Element productDescriptor = doc.getRootElement().getChild("product-descriptor");
    if (productDescriptor != null) {
      Span.current().addEvent(toPublish ? "patch" : "skip" + " " + pluginModuleName + " <product-descriptor/>");

      setProductDescriptorEapAttribute(productDescriptor, isEap);

      productDescriptor.setAttribute("release-date", myReleaseDate);
      productDescriptor.setAttribute("release-version", myReleaseVersion);

      if (!toPublish && !retainProductDescriptorForBundledPlugin) {
        removeTextBeforeElement(productDescriptor);
        productDescriptor.remove();
      }
    }


    // Patch Database plugin for WebStorm, see WEB-48278
    if (toPublish &&
        productDescriptor != null &&
        productDescriptor.getAttributeValue("code").equals("PDB") &&
        productName.equals("WebStorm")) {
      Span.current().addEvent("patch " + pluginModuleName + " for WebStorm");

      Element pluginName = doc.getRootElement().getChild("name");
      if (!pluginName.getText().equals("Database Tools and SQL")) {
        throw new IllegalStateException("Plugin name for \'" + pluginModuleName + "\' should be \'Database Tools and SQL\'");
      }

      pluginName.setText("Database Tools and SQL for WebStorm");

      Element description = doc.getRootElement().getChild("description");
      boolean replaced = replaceInElementText(description, "IntelliJ-based IDEs", "WebStorm");
      if (!replaced) {
        throw new IllegalStateException("Could not find \'IntelliJ-based IDEs\' in plugin description of " + pluginModuleName);
      }
    }


    return doc.toXML();
  }

  private static void removeTextBeforeElement(final Element element) {
    final Element parentElement = element.getParentElement();
    if (parentElement == null) {
      throw new IllegalStateException("Could not find parent of \'" + element.toXML() + "\'");
    }


    int elementIndex = parentElement.nodeIndexOf(element);
    if (elementIndex < 0) {
      throw new IllegalStateException(
        "Could not find element index \'" + element.toXML() + "\' in parent \'" + parentElement.toXML() + "\'");
    }


    if (elementIndex > 0) {
      Node text = parentElement.getNode(elementIndex - 1);
      if (text instanceof Text) {
        parentElement.removeNode(elementIndex - 1);
      }
    }
  }

  private static Element getOrCreateTopElement(final Document doc, String tagName, List<String> anchors) {
    Element child = doc.getRootElement().getChild(tagName);
    if (child != null) {
      return ((Element)(child));
    }


    Element newElement = new Element(tagName);

    final Element anchor = DefaultGroovyMethods.find(DefaultGroovyMethods.collect(anchors, new Closure<Element>(null, null) {
      public Element doCall(String it) { return doc.getRootElement().getChild(it); }

      public Element doCall() {
        return doCall(null);
      }
    }), new Closure<Boolean>(null, null) {
      public Boolean doCall(Element it) { return it != null; }

      public Boolean doCall() {
        return doCall(null);
      }
    });
    if (anchor == null) {
      doc.getRootElement().addNode(0, newElement);
      doc.getRootElement().addNode(0, new Text("\n  "));
    }
    else {
      int anchorIndex = doc.getRootElement().nodeIndexOf(anchor);
      if (anchorIndex < 0) {
        // Should not happen
        throw new IllegalStateException(
          "anchor < 0 when getting child index of \'" + anchor.getName() + "\' in root element of " + doc.toXML());
      }


      Node indent = doc.getRootElement().getNode(anchorIndex - 1);
      if (indent instanceof Text) {
        indent = ((Text)indent).copy();
      }
      else {
        indent = new Text("");
      }


      doc.getRootElement().addNode(anchorIndex + 1, newElement);
      doc.getRootElement().addNode(anchorIndex + 1, indent);
    }


    return ((Element)(newElement));
  }

  private static void setProductDescriptorEapAttribute(Element productDescriptor, boolean isEap) {
    if (isEap) {
      productDescriptor.setAttribute("eap", "true");
    }
    else {
      productDescriptor.removeAttribute("eap");
    }
  }

  private static boolean replaceInElementText(Element element, String oldText, String newText) {
    boolean replaced = false;
    for (Node node : element.getNodes()) {
      if (node instanceof Text) {
        String textBefore = ((Text)node).getText();
        String text = textBefore.replace(oldText, newText);
        if (!textBefore.equals(text)) {
          replaced = true;
          ((Text)node).setText(text);
        }
      }
    }


    return replaced;
  }

  public static DateTimeFormatter getPluginDateFormat() {
    return pluginDateFormat;
  }

  private static final DateTimeFormatter pluginDateFormat = DateTimeFormatter.ofPattern("yyyyMMdd");
  private final String myReleaseDate;
  private final String myReleaseVersion;
}
