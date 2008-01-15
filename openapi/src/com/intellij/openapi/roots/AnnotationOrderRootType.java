package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class AnnotationOrderRootType extends OrderRootType {
  /**
   * External annotations path
   */
  public static OrderRootType getInstance() {
    return getOrderRootType(AnnotationOrderRootType.class);
  }

  private AnnotationOrderRootType() {
    super("ANNOTATIONS", "annotationsPath", "annotation-paths", true);
  }

  public static VirtualFile[] getFiles(OrderEntry entry) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    RecursiveRootPolicy<List<VirtualFile>> policy = new RecursiveRootPolicy<List<VirtualFile>>() {
      public List<VirtualFile> visitLibraryOrderEntry(final LibraryOrderEntry orderEntry, final List<VirtualFile> value) {
        Collections.addAll(value, orderEntry.getRootFiles(getInstance()));
        return value;
      }

      public List<VirtualFile> visitJdkOrderEntry(final JdkOrderEntry orderEntry, final List<VirtualFile> value) {
        Collections.addAll(value, orderEntry.getRootFiles(getInstance()));
        return value;
      }

      public List<VirtualFile> visitModuleSourceOrderEntry(final ModuleSourceOrderEntry orderEntry,
                                                           final List<VirtualFile> value) {
        Collections.addAll(value, orderEntry.getRootModel().getRootPaths(getInstance()));
        return value;
      }
    };
    entry.accept(policy, result);
    return result.toArray(new VirtualFile[result.size()]);
  }

  public static String[] getUrls(OrderEntry entry) {
    List<String> result = new ArrayList<String>();
    RecursiveRootPolicy<List<String>> policy = new RecursiveRootPolicy<List<String>>() {
      public List<String> visitLibraryOrderEntry(final LibraryOrderEntry orderEntry, final List<String> value) {
        Collections.addAll(value, orderEntry.getRootUrls(getInstance()));
        return value;
      }

      public List<String> visitJdkOrderEntry(final JdkOrderEntry orderEntry, final List<String> value) {
        Collections.addAll(value, orderEntry.getRootUrls(getInstance()));
        return value;
      }

      public List<String> visitModuleSourceOrderEntry(final ModuleSourceOrderEntry orderEntry,
                                                           final List<String> value) {
        Collections.addAll(value, orderEntry.getRootModel().getRootUrls(getInstance()));
        return value;
      }
    };
    entry.accept(policy, result);
    return result.toArray(new String[result.size()]);
  }
}
