package com.intellij.usageView;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 11.08.2005
 * Time: 16:04:31
 * To change this template use File | Settings | File Templates.
 */
public class UsageViewBundle {
  private static Reference<ResourceBundle> ourBundle;

  @NonNls private static final String BUNDLE = "messages.UsageView";

  private UsageViewBundle() {
  }

  @SuppressWarnings({"AutoBoxing"})
  public static String getUsagesString(int usagesCount, int filesCount) {
    return "( " + message("occurence.info.usage", usagesCount, filesCount) + " )";
  }

  @SuppressWarnings({"AutoBoxing"})
  public static String getOccurencesString(int usagesCount, int filesCount) {
    return "( " + message("occurence.info.occurence", usagesCount, filesCount) + " )";
  }

  @SuppressWarnings({"AutoBoxing"})
  public static String getReferencesString(int usagesCount, int filesCount) {
    return "( " + message("occurence.info.reference", usagesCount, filesCount) + " )";
  }

  public static String message(@PropertyKey(resourceBundle = BUNDLE)String key, Object... params) {
    return CommonBundle.message(getBundle(), key, params);
  }

  private static ResourceBundle getBundle() {
    ResourceBundle bundle = null;
    if (ourBundle != null) bundle = ourBundle.get();
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(BUNDLE);
      ourBundle = new SoftReference<ResourceBundle>(bundle);
    }
    return bundle;
  }
}
