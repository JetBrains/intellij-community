/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.dependencyAnalysis;

import com.intellij.icons.AllIcons;
import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ui.CellAppearanceEx;
import com.intellij.openapi.roots.ui.OrderEntryAppearanceService;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * Analyzer for  module classpath. It uses order enumerator to get classpath details.
 */
public class ModuleDependenciesAnalyzer {
  /**
   * The current module
   */
  private final Module myModule;
  /**
   * If true production classpath is analyzed. If false, the test classpath.
   */
  private final boolean myProduction;
  /**
   * If true the compilation classpath is analyzed. If false, the test classpath.
   */
  private final boolean myCompile;
  /**
   * If true, SDK classes are included in the classpath.
   */
  private final boolean mySdk;
  /**
   * The order entry explanations
   */
  private final List<OrderEntryExplanation> myOrderEntries = new ArrayList<>();
  /**
   * The url explanations
   */
  private final List<UrlExplanation> myUrls = new ArrayList<>();

  /**
   * The constructor (it creates explanations immediately
   *
   * @param module     the context module
   * @param production the production/test flag
   * @param compile    the compile/runtime flag
   * @param sdk        the include sdk paths
   */
  public ModuleDependenciesAnalyzer(Module module, boolean production, boolean compile, boolean sdk) {
    myModule = module;
    myProduction = production;
    myCompile = compile;
    mySdk = sdk;
    analyze();
  }

  /**
   * @return url explanations
   */
  public List<UrlExplanation> getUrls() {
    return Collections.unmodifiableList(myUrls);
  }

  /**
   * @return order entry explanations
   */
  public List<OrderEntryExplanation> getOrderEntries() {
    return Collections.unmodifiableList(myOrderEntries);
  }

  /**
   * Analyze module classpath
   */
  private void analyze() {
    OrderEnumerator e = ModuleRootManager.getInstance(myModule).orderEntries();
    e.recursively();
    if (!mySdk) {
      e.withoutSdk();
    }
    if (myCompile) {
      e.compileOnly();
    }
    else {
      e.runtimeOnly();
    }
    if (myProduction) {
      e.productionOnly();
    }
    final Map<String, List<OrderPath>> urlExplanations = new LinkedHashMap<>();
    final OrderRootsEnumerator classes = e.classes();
    if (myCompile) {
      classes.withoutSelfModuleOutput();
    }
    for (String url : classes.getUrls()) {
      if (!urlExplanations.containsKey(url)) {
        urlExplanations.put(url, new ArrayList<>());
      }
    }
    final Map<OrderEntry, List<OrderPath>> orderExplanations = new LinkedHashMap<>();
    new PathWalker(urlExplanations, orderExplanations).examine(myModule, 0);
    for (Map.Entry<OrderEntry, List<OrderPath>> entry : orderExplanations.entrySet()) {
      myOrderEntries.add(new OrderEntryExplanation(entry.getKey(), entry.getValue()));
    }
    for (Map.Entry<String, List<OrderPath>> entry : urlExplanations.entrySet()) {
      myUrls.add(new UrlExplanation(entry.getKey(), entry.getValue()));
    }
  }

  /**
   * The walker for the class paths. It walks the entire module classpath
   */
  private class PathWalker {
    /**
     * The explanations for urls
     */
    private final Map<String, List<OrderPath>> myUrlExplanations;
    /**
     * The explanations for order entries
     */
    private final Map<OrderEntry, List<OrderPath>> myOrderExplanations;
    /**
     * The current stack
     */
    private final ArrayList<OrderPathElement> myStack = new ArrayList<>();
    /**
     * Visited modules (in order to detect cyclic dependencies)
     */
    private final HashSet<Module> myVisited = new HashSet<>();

    /**
     * The constructor
     *
     * @param urlExplanations   the url explanations to accumulate
     * @param orderExplanations the explanations for order entries
     */
    public PathWalker(Map<String, List<OrderPath>> urlExplanations,
                      Map<OrderEntry, List<OrderPath>> orderExplanations) {
      myUrlExplanations = urlExplanations;
      myOrderExplanations = orderExplanations;
    }

    /**
     * Examine the specified module
     *
     * @param m     the module to examine
     * @param level the level of the examination
     */
    void examine(final Module m, final int level) {
      if (myVisited.contains(m)) {
        return;
      }
      myVisited.add(m);
      try {
        final OrderEnumerator e = ModuleRootManager.getInstance(m).orderEntries();
        if (!mySdk || level != 0) {
          e.withoutSdk();
        }
        if (myCompile && level != 0) {
          e.exportedOnly();
        }
        if (myProduction) {
          e.productionOnly();
        }
        if (myCompile) {
          e.compileOnly();
        }
        else {
          e.runtimeOnly();
        }
        e.forEach(orderEntry -> {
          myStack.add(new OrderEntryPathElement(orderEntry));
          try {
            if (orderEntry instanceof ModuleOrderEntry) {
              ModuleOrderEntry o = (ModuleOrderEntry)orderEntry;
              examine(o.getModule(), level + 1);
            }
            else if (orderEntry instanceof ModuleSourceOrderEntry) {
              if (!myProduction || !myCompile) {
                CompilerModuleExtension e1 = CompilerModuleExtension.getInstance(m);
                final OrderPath p = new OrderPath(myStack);
                for (String u : e1.getOutputRootUrls(!myCompile ? !myProduction : level > 0 && !myProduction)) {
                  addUrlPath(p, u);
                }
                addEntryPath(orderEntry, p);
              }
            }
            else {
              final OrderPath p = new OrderPath(myStack);
              for (String u : orderEntry.getUrls(OrderRootType.CLASSES)) {
                addUrlPath(p, u);
              }
              addEntryPath(orderEntry, p);
            }
          }
          finally {
            myStack.remove(myStack.size() - 1);
          }
          return true;
        });
      }
      finally {
        myVisited.remove(m);
      }
    }

    /**
     * Add url path
     *
     * @param p the path to add
     * @param u the url to update
     */
    private void addUrlPath(OrderPath p, String u) {
      final List<OrderPath> orderPaths = myUrlExplanations.get(u);
      if (orderPaths != null) {
        orderPaths.add(p);
      }
    }

    /**
     * Add order entry explanation
     *
     * @param orderEntry the order entry to explain
     * @param p          the path that explain order entry
     */
    private void addEntryPath(OrderEntry orderEntry, OrderPath p) {
      List<OrderPath> paths = myOrderExplanations.get(orderEntry);
      if (paths == null) {
        paths = new ArrayList<>();
        myOrderExplanations.put(orderEntry, paths);
      }
      paths.add(p);
    }
  }

  /**
   * The path consisting of order entry path.
   */
  public static class OrderPath {
    /**
     * The immutable list of path elements. The first element in the list is an order entry actually included in the current module.
     */
    private final List<OrderPathElement> myEntries;

    /**
     * The constructor
     *
     * @param entries the list of entries (will be copied and wrapped)
     */
    public OrderPath(List<OrderPathElement> entries) {
      this.myEntries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /**
     * @return the immutable list of path elements. The first element in the list is an order entry actually included in the current module.
     */
    public List<OrderPathElement> entries() {
      return myEntries;
    }

    @Override
    public int hashCode() {
      return myEntries.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof OrderPath)) {
        return false;
      }
      return myEntries.equals(((OrderPath)obj).myEntries);
    }
  }

  /**
   * The a entry in the path. The implementation should support {@link #equals(Object)} and {@link #hashCode()} methods.
   */
  public static abstract class OrderPathElement {
    /**
     * Get appearance for path element
     *
     * @param isSelected true if the element is selected
     * @return the appearance to use for rendering
     */
    @NotNull
    public abstract CellAppearanceEx getAppearance(boolean isSelected);
  }

  /**
   * The order entry path element
   */
  public static class OrderEntryPathElement extends OrderPathElement {
    /**
     * The order entry
     */
    private final OrderEntry myEntry;

    /**
     * The constructor
     *
     * @param entry the order entry
     */
    public OrderEntryPathElement(OrderEntry entry) {
      this.myEntry = entry;
    }

    /**
     * @return the order entry
     */
    public OrderEntry entry() {
      return myEntry;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
      return myEntry.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof OrderEntryPathElement)) {
        return false;
      }
      OrderEntryPathElement o = (OrderEntryPathElement)obj;
      return o.myEntry == myEntry;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
      return myEntry.getPresentableName();
    }

    /**
     * {@inheritDoc}
     */
    @NotNull
    @Override
    public CellAppearanceEx getAppearance(boolean isSelected) {
      return OrderEntryAppearanceService.getInstance().forOrderEntry(myEntry.getOwnerModule().getProject(), myEntry, isSelected);
    }
  }

  /**
   * The base class for explanations
   */
  public static class Explanation {
    /**
     * The paths that refer to the path element
     */
    public final List<OrderPath> myPaths;

    /**
     * The explanation for the path
     *
     * @param paths the paths to analyze (the list is wrapped)
     */
    Explanation(List<OrderPath> paths) {
      this.myPaths = Collections.unmodifiableList(paths);
    }

    /**
     * @return the paths that refer to the path element
     */
    public List<OrderPath> paths() {
      return myPaths;
    }
  }

  /**
   * The explanation for
   */
  public static class OrderEntryExplanation extends Explanation {
    /**
     * The URL in the path
     */
    private final OrderEntry myEntry;

    /**
     * The explanation for the path
     *
     * @param entry the explained order entry
     * @param paths the paths to analyze
     */
    OrderEntryExplanation(OrderEntry entry, List<OrderPath> paths) {
      super(paths);
      myEntry = entry;
    }

    /**
     * @return the explained entry
     */
    public OrderEntry entry() {
      return myEntry;
    }
  }


  /**
   * The explanation for url
   */
  public static class UrlExplanation extends Explanation {
    /**
     * The URL in the path
     */
    private final String myUrl;

    /**
     * The explanation for the path
     *
     * @param url   the url for the order entry
     * @param paths the paths to analyze
     */
    UrlExplanation(String url, List<OrderPath> paths) {
      super(paths);
      myUrl = url;
    }

    /**
     * @return the explained url
     */
    public String url() {
      return myUrl;
    }

    /**
     * @return icon for the classpath
     */
    @Nullable
    public Icon getIcon() {
      VirtualFile file = getLocalFile();
      return file == null ? AllIcons.General.Error : VirtualFilePresentation.getIcon(file);
    }

    /**
     * @return the local file
     */
    @Nullable
    public VirtualFile getLocalFile() {
      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(myUrl);
      if (file != null) {
        file = VfsUtil.getLocalFile(file);
      }
      return file;
    }
  }
}
