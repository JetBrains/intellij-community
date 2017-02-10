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

/*
 * User: anna
 * Date: 05-Jan-2007
 */
package com.intellij.codeInspection.offlineViewer;

import com.intellij.codeInspection.InspectionApplication;
import com.intellij.codeInspection.offline.OfflineProblemDescriptor;
import com.intellij.codeInspection.reference.SmartRefElementPointerImpl;
import com.thoughtworks.xstream.io.xml.XppReader;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OfflineViewParseUtil {
  @NonNls private static final String PACKAGE = "package";
  @NonNls private static final String DESCRIPTION = "description";
  @NonNls private static final String HINTS = "hints";
  @NonNls private static final String LINE = "line";
  @NonNls private static final String MODULE = "module";

  private OfflineViewParseUtil() {
  }

  public static Map<String, Set<OfflineProblemDescriptor>> parse(final String problems) {
    final TObjectIntHashMap<String> fqName2IdxMap = new TObjectIntHashMap<>();
    final Map<String, Set<OfflineProblemDescriptor>> package2Result = new THashMap<>();
    final XppReader reader = new XppReader(new StringReader(problems));
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
            int idx = fqName2IdxMap.get(fqName);
            descriptor.setProblemIndex(idx);
            fqName2IdxMap.put(fqName, idx + 1);
          }
          if (DESCRIPTION.equals(reader.getNodeName())) {
            descriptor.setDescription(reader.getValue());
          }
          if (LINE.equals(reader.getNodeName())) {
            descriptor.setLine(Integer.parseInt(reader.getValue()));
          }
          if (MODULE.equals(reader.getNodeName())) {
            descriptor.setModule(reader.getValue());
          }
          if (HINTS.equals(reader.getNodeName())) {
            while(reader.hasMoreChildren()) {
              reader.moveDown();
              List<String> hints = descriptor.getHints();
              if (hints == null) {
                hints = new ArrayList<>();
                descriptor.setHints(hints);
              }
              hints.add(reader.getAttribute("value"));
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
        if (!added) appendDescriptor(package2Result, "", descriptor);
        reader.moveUp();
      }
    }
    finally {
      reader.close();
    }
    return package2Result;
  }

  private static void appendDescriptor(final Map<String, Set<OfflineProblemDescriptor>> package2Result,
                                       final String packageName,
                                       final OfflineProblemDescriptor descriptor) {
    Set<OfflineProblemDescriptor> descriptors = package2Result.get(packageName);
    if (descriptors == null) {
      descriptors = new THashSet<>();
      package2Result.put(packageName, descriptors);
    }
    descriptors.add(descriptor);
  }

  @Nullable
  public static String parseProfileName(String descriptors) {
    final XppReader reader = new XppReader(new StringReader(descriptors));
    try {
      return reader.getAttribute(InspectionApplication.PROFILE);
    }
    catch (Exception e) {
      return null;
    }
    finally {
      reader.close();
    }
  }
}