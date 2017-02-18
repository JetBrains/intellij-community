package com.intellij.dupLocator;

import com.intellij.dupLocator.treeHash.FragmentsCollector;
import com.intellij.lang.Language;
import com.intellij.openapi.extensions.Extensions;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: May 18, 2009
 * Time: 7:39:35 PM
 * To change this template use File | Settings | File Templates.
 */
public class DuplicatesProfileCache {
  private static final Map<DupInfo, TIntObjectHashMap<DuplicatesProfile>> ourProfileCache = new HashMap<>();

  private DuplicatesProfileCache() {
  }

  public static void clear(@NotNull DupInfo info) {
    ourProfileCache.remove(info);
  }

  @Nullable
  public static DuplicatesProfile getProfile(@NotNull DupInfo dupInfo, int index) {
    TIntObjectHashMap<DuplicatesProfile> patternCache = ourProfileCache.get(dupInfo);
    if (patternCache == null) {
      patternCache = new TIntObjectHashMap<>();
      ourProfileCache.put(dupInfo, patternCache);
    }
    DuplicatesProfile result = patternCache.get(index);
    if (result == null) {
      DuplicatesProfile[] profiles = Extensions.getExtensions(DuplicatesProfile.EP_NAME);
      DuplicatesProfile theProfile = null;
      for (DuplicatesProfile profile : profiles) {
        if (profile.isMyDuplicate(dupInfo, index)) {
          theProfile = profile;
          break;
        }
      }
      result = theProfile == null ? NULL_PROFILE : theProfile;
      patternCache.put(index, result);
    }
    return result == NULL_PROFILE ? null : result;
  }

  private static final DuplicatesProfile NULL_PROFILE = new DuplicatesProfile() {
    @NotNull
    @Override
    public DuplocateVisitor createVisitor(@NotNull FragmentsCollector collector) {
      return null;
    }

    @Override
    public boolean isMyLanguage(@NotNull Language language) {
      return false;
    }

    @NotNull
    @Override
    public DuplocatorState getDuplocatorState(@NotNull Language language) {
      return null;
    }

    @Override
    public boolean isMyDuplicate(@NotNull DupInfo info, int index) {
      return false;
    }
  };
}
