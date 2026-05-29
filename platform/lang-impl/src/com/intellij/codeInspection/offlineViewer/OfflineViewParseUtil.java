// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.offlineViewer;

import com.intellij.codeInspection.InspectionsResultUtil;
import com.intellij.codeInspection.offline.OfflineProblemDescriptor;
import com.intellij.codeInspection.reference.SmartRefElementPointerImpl;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.Interner;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OfflineViewParseUtil {
  private static final @NonNls String PACKAGE = "package";
  private static final @NonNls String DESCRIPTION = "description";
  private static final @NonNls String HINTS = "hints";
  private static final @NonNls String LINE = "line";
  private static final @NonNls String OFFSET = "offset";
  private static final @NonNls String MODULE = "module";

  private OfflineViewParseUtil() {
  }

  public static Map<String, Set<OfflineProblemDescriptor>> parse(Path problemFile) throws IOException {
    try (Reader reader = Files.newBufferedReader(problemFile)) {
      return parse(reader);
    }
  }

  public static Map<String, Set<OfflineProblemDescriptor>> parse(Reader problemReader) {
    Object2IntMap<String> fqName2IdxMap = new Object2IntOpenHashMap<>();
    Interner<@NlsSafe String> stringInterner = Interner.createStringInterner();
    Map<String, Set<OfflineProblemDescriptor>> package2Result = new HashMap<>();
    XMLStreamReader reader = null;
    try {
      reader = createXmlStreamReader(problemReader);
      while (reader.hasNext()) {
        if (reader.next() == XMLStreamConstants.START_ELEMENT && "problem".equals(reader.getLocalName())) {
          parseProblem(reader, fqName2IdxMap, stringInterner, package2Result);
        }
      }
    }
    catch (XMLStreamException e) {
      throw new RuntimeException(e);
    }
    finally {
      closeReader(reader);
    }
    return package2Result;
  }

  private static void parseProblem(@NotNull XMLStreamReader reader,
                                   @NotNull Object2IntMap<String> fqName2IdxMap,
                                   @NotNull Interner<@NlsSafe String> stringInterner,
                                   @NotNull Map<String, Set<OfflineProblemDescriptor>> package2Result) throws XMLStreamException {
    final OfflineProblemDescriptor descriptor = new OfflineProblemDescriptor();
    final List<String> packageNames = new ArrayList<>();
    int depth = 0;
    int hintsDepth = -1;
    int textDepth = -1;
    String textElement = null;
    StringBuilder text = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        depth++;
        String nodeName = reader.getLocalName();
        if (depth == 1 && SmartRefElementPointerImpl.ENTRY_POINT.equals(nodeName)) {
          parseEntryPoint(reader, descriptor, fqName2IdxMap);
        }
        if (depth == 1 && HINTS.equals(nodeName)) {
          hintsDepth = depth;
        }
        else if (hintsDepth >= 0 && depth == hintsDepth + 1) {
          String hintValue = reader.getAttributeValue(null, "value");
          if (hintValue == null) {
            throw new XMLStreamException("Missing 'value' attribute in inspection hint", reader.getLocation());
          }
          addHint(descriptor, stringInterner, hintValue);
        }
        if (isTextElement(nodeName, depth)) {
          textDepth = depth;
          textElement = nodeName;
          text = new StringBuilder();
        }
      }
      else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
        if (text != null) {
          text.append(reader.getText());
        }
      }
      else if (event == XMLStreamConstants.END_ELEMENT) {
        if (depth == 0 && "problem".equals(reader.getLocalName())) {
          break;
        }
        if (depth == textDepth && reader.getLocalName().equals(textElement)) {
          applyText(descriptor, stringInterner, packageNames, textElement, text.toString());
          textDepth = -1;
          textElement = null;
          text = null;
        }
        if (depth == hintsDepth && HINTS.equals(reader.getLocalName())) {
          hintsDepth = -1;
        }
        depth--;
      }
    }

    if (packageNames.isEmpty()) {
      appendDescriptor(package2Result, null, descriptor);
    }
    else {
      for (String packageName : packageNames) {
        appendDescriptor(package2Result, packageName, descriptor);
      }
    }
  }

  private static void parseEntryPoint(@NotNull XMLStreamReader reader,
                                      @NotNull OfflineProblemDescriptor descriptor,
                                      @NotNull Object2IntMap<String> fqName2IdxMap) {
    descriptor.setType(reader.getAttributeValue(null, SmartRefElementPointerImpl.TYPE_ATTR));
    final String fqName = reader.getAttributeValue(null, SmartRefElementPointerImpl.FQNAME_ATTR);
    descriptor.setFQName(fqName);

    if (!fqName2IdxMap.containsKey(fqName)) {
      fqName2IdxMap.put(fqName, 0);
    }
    int idx = fqName2IdxMap.getInt(fqName);
    descriptor.setProblemIndex(idx);
    fqName2IdxMap.put(fqName, idx + 1);
  }

  private static boolean isTextElement(@NotNull String nodeName, int depth) {
    return PACKAGE.equals(nodeName) ||
           depth == 1 && (DESCRIPTION.equals(nodeName) || LINE.equals(nodeName) || OFFSET.equals(nodeName) || MODULE.equals(nodeName));
  }

  private static void applyText(@NotNull OfflineProblemDescriptor descriptor,
                                @NotNull Interner<@NlsSafe String> stringInterner,
                                @NotNull List<String> packageNames,
                                @NotNull String nodeName,
                                @NotNull String value) {
    if (DESCRIPTION.equals(nodeName)) {
      descriptor.setDescription(stringInterner.intern(value));
    }
    else if (LINE.equals(nodeName)) {
      descriptor.setLine(Integer.parseInt(value));
    }
    else if (OFFSET.equals(nodeName)) {
      descriptor.setOffset(Integer.parseInt(value));
    }
    else if (MODULE.equals(nodeName)) {
      descriptor.setModule(stringInterner.intern(value));
    }
    else if (PACKAGE.equals(nodeName)) {
      packageNames.add(value);
    }
  }

  private static void addHint(@NotNull OfflineProblemDescriptor descriptor,
                              @NotNull Interner<@NlsSafe String> stringInterner,
                              @NotNull String value) {
    List<String> hints = descriptor.getHints();
    if (hints == null) {
      hints = new ArrayList<>();
      descriptor.setHints(hints);
    }
    hints.add(stringInterner.intern(value));
  }

  public static @Nullable String parseProfileName(@NotNull Path descriptorFile) throws IOException {
    try (Reader reader = Files.newBufferedReader(descriptorFile)) {
      return parseProfileName(reader);
    }
  }

  public static @Nullable String parseProfileName(Reader descriptorReader) {
    XMLStreamReader reader = null;
    try {
      reader = createXmlStreamReader(descriptorReader);
      while (reader.hasNext()) {
        if (reader.next() == XMLStreamConstants.START_ELEMENT) {
          return reader.getAttributeValue(null, InspectionsResultUtil.PROFILE);
        }
      }
      return null;
    }
    catch (Exception e) {
      return null;
    }
    finally {
      closeReader(reader);
    }
  }

  private static @NotNull XMLStreamReader createXmlStreamReader(@NotNull Reader reader) throws XMLStreamException {
    XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    return factory.createXMLStreamReader(reader);
  }

  private static void closeReader(@Nullable XMLStreamReader reader) {
    if (reader == null) return;
    try {
      reader.close();
    }
    catch (XMLStreamException ignored) {
    }
  }

  private static void appendDescriptor(final Map<String, Set<OfflineProblemDescriptor>> package2Result,
                                       final String packageName,
                                       final OfflineProblemDescriptor descriptor) {
    Set<OfflineProblemDescriptor> descriptors = package2Result.get(packageName);
    if (descriptors == null) {
      descriptors = new HashSet<>();
      package2Result.put(packageName, descriptors);
    }
    descriptors.add(descriptor);
  }
}
