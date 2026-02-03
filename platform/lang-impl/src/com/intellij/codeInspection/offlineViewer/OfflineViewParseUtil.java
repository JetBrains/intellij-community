// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.offlineViewer;

import com.intellij.codeInspection.InspectionsResultUtil;
import com.intellij.codeInspection.offline.OfflineProblemDescriptor;
import com.intellij.codeInspection.reference.SmartRefElementPointerImpl;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.Interner;
import com.thoughtworks.xstream.io.xml.XppReader;
import io.github.xstream.mxparser.MXParser;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

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
    return parse(Files.newBufferedReader(problemFile));
  }

  /**
   * @deprecated use {@link #parse(File)} or {@link #parse(Reader)}
   */
  @Deprecated(forRemoval = true)
  public static Map<String, Set<OfflineProblemDescriptor>> parse(String problemText) {
    return parse(new StringReader(problemText));
  }

  public static Map<String, Set<OfflineProblemDescriptor>> parse(Reader problemReader) {
    Object2IntMap<String> fqName2IdxMap = new Object2IntOpenHashMap<>();
    Interner<@NlsSafe String> stringInterner = Interner.createStringInterner();
    Map<String, Set<OfflineProblemDescriptor>> package2Result = new HashMap<>();
    XppReader reader = new XppReader(problemReader, new MXParser());
    try {
      while(reader.hasMoreChildren()) {
        reader.moveDown(); //problem
        final OfflineProblemDescriptor descriptor = new OfflineProblemDescriptor();
        boolean added = false;
        while(reader.hasMoreChildren()) {
          reader.moveDown();
          if (SmartRefElementPointerImpl.ENTRY_POINT.equals(reader.getNodeName())) {
            descriptor.setType(reader.getAttribute(SmartRefElementPointerImpl.TYPE_ATTR));
            final String fqName = reader.getAttribute(SmartRefElementPointerImpl.FQNAME_ATTR);
            descriptor.setFQName(fqName);

            if (!fqName2IdxMap.containsKey(fqName)) {
              fqName2IdxMap.put(fqName, 0);
            }
            int idx = fqName2IdxMap.getInt(fqName);
            descriptor.setProblemIndex(idx);
            fqName2IdxMap.put(fqName, idx + 1);
          }
          if (DESCRIPTION.equals(reader.getNodeName())) {
            descriptor.setDescription(stringInterner.intern(reader.getValue()));
          }
          if (LINE.equals(reader.getNodeName())) {
            descriptor.setLine(Integer.parseInt(reader.getValue()));
          }
          if(OFFSET.equals(reader.getNodeName())) {
            descriptor.setOffset(Integer.parseInt(reader.getValue()));
          }
          if (MODULE.equals(reader.getNodeName())) {
            descriptor.setModule(stringInterner.intern(reader.getValue()));
          }
          if (HINTS.equals(reader.getNodeName())) {
            while(reader.hasMoreChildren()) {
              reader.moveDown();
              List<String> hints = descriptor.getHints();
              if (hints == null) {
                hints = new ArrayList<>();
                descriptor.setHints(hints);
              }
              hints.add(stringInterner.intern(reader.getAttribute("value")));
              reader.moveUp();
            }
          }
          if (PACKAGE.equals(reader.getNodeName())) {
            appendDescriptor(package2Result, reader.getValue(), descriptor);
            added = true;
          }
          while(reader.hasMoreChildren()) {
            reader.moveDown();
            if (PACKAGE.equals(reader.getNodeName())) {
              appendDescriptor(package2Result, reader.getValue(), descriptor);
              added = true;
            }
            reader.moveUp();
          }
          reader.moveUp();
        }
        if (!added) appendDescriptor(package2Result, null, descriptor);
        reader.moveUp();
      }
    }
    finally {
      reader.close();
    }
    return package2Result;
  }

  public static @Nullable String parseProfileName(@NotNull Path descriptorFile) throws IOException {
    return parseProfileName(Files.newBufferedReader(descriptorFile));
  }

  /**
   * @deprecated use {@link #parseProfileName(File)} or {@link #parseProfileName(Reader)}
   */
  @Deprecated(forRemoval = true)
  public static @Nullable String parseProfileName(String descriptorText) {
    return parseProfileName(new StringReader(descriptorText));
  }

  public static @Nullable String parseProfileName(Reader descriptorReader) {
    final XppReader reader = new XppReader(descriptorReader, new MXParser());
    try {
      return reader.getAttribute(InspectionsResultUtil.PROFILE);
    }
    catch (Exception e) {
      return null;
    }
    finally {
      reader.close();
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
