package org.jetbrains.debugger.memory.utils;

import com.intellij.openapi.util.text.StringUtil;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

class NamesUtils {
  @NotNull
  static String getUniqueName(@NotNull ObjectReference ref) {
    String shortName = StringUtil.getShortName(ref.referenceType().name());
    String name = shortName.replace("[]", "Array");
    return String.format("%s@%d", name, ref.uniqueID());
  }

  @NotNull
  static String getArrayUniqueName(@NotNull ArrayReference ref) {
    String shortName = StringUtil.getShortName(ref.referenceType().name());
    int length = ref.length();

    String name = shortName.replaceFirst(Pattern.quote("[]"), String.format("[%d]", length));
    return String.format("%s@%d", name, ref.uniqueID());
  }
}
