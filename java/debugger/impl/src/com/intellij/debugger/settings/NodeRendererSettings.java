/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.debugger.settings;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.impl.watch.ArrayElementDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.render.*;
import com.intellij.debugger.ui.tree.render.Renderer;
import com.intellij.openapi.components.*;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.util.EventDispatcher;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.InternalIterator;
import com.sun.jdi.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: lex
 * Date: Sep 18, 2003
 * Time: 8:00:25 PM
 */
@State(
  name="NodeRendererSettings",
  storages= {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/debugger.renderers.xml"
    )}
)
public class NodeRendererSettings implements PersistentStateComponent<Element> {
  @NonNls private static final String REFERENCE_RENDERER = "Reference renderer";
  @NonNls public static final String RENDERER_TAG = "Renderer";
  @NonNls private static final String RENDERER_ID = "ID";

  private final EventDispatcher<NodeRendererSettingsListener> myDispatcher = EventDispatcher.create(NodeRendererSettingsListener.class);
  private final List<NodeRenderer> myPluginRenderers = new ArrayList<NodeRenderer>();
  private RendererConfiguration myCustomRenderers = new RendererConfiguration(this);

  // base renderers
  private final PrimitiveRenderer myPrimitiveRenderer = new PrimitiveRenderer();
  private final ArrayRenderer myArrayRenderer = new ArrayRenderer();
  private final ClassRenderer myClassRenderer = new ClassRenderer();
  private final HexRenderer myHexRenderer = new HexRenderer();
  private final ToStringRenderer myToStringRenderer = new ToStringRenderer();
  // alternate collections
  private final NodeRenderer[] myAlternateCollectionRenderers = new NodeRenderer[]{
    createCompoundReferenceRenderer(
      "Map", CommonClassNames.JAVA_UTIL_MAP,
      createLabelRenderer(" size = ", "size()", null),
      createExpressionChildrenRenderer("entrySet().toArray()", "!isEmpty()")
    ),
    createCompoundReferenceRenderer(
      "Map.Entry", "java.util.Map$Entry",
      new MapEntryLabelRenderer()/*createLabelRenderer(null, "\" \" + getKey() + \" -> \" + getValue()", null)*/,
      createEnumerationChildrenRenderer(new String[][]{{"key", "getKey()"}, {"value", "getValue()"}})
    ),
    createCompoundReferenceRenderer(
      "List", CommonClassNames.JAVA_UTIL_LIST,
      createLabelRenderer(" size = ", "size()", null),
      new ListChildrenRenderer()
    ),
    createCompoundReferenceRenderer(
      "Collection", "java.util.Collection",
      createLabelRenderer(" size = ", "size()", null),
      createExpressionChildrenRenderer("toArray()", "!isEmpty()")
    )
  };
  @NonNls private static final String HEX_VIEW_ENABLED = "HEX_VIEW_ENABLED";
  @NonNls private static final String ALTERNATIVE_COLLECTION_VIEW_ENABLED = "ALTERNATIVE_COLLECTION_VIEW_ENABLED";
  @NonNls private static final String CUSTOM_RENDERERS_TAG_NAME = "CustomRenderers";
  
  public NodeRendererSettings() {
    // default configuration
    myHexRenderer.setEnabled(false);
    myToStringRenderer.setEnabled(true);
    setAlternateCollectionViewsEnabled(true);
  }
  
  public static NodeRendererSettings getInstance() {
    return ServiceManager.getService(NodeRendererSettings.class);
  }

  /**
   * use {@link com.intellij.debugger.ui.tree.render.NodeRenderer} extension
   * @param renderer
   */
  @Deprecated
  public void addPluginRenderer(NodeRenderer renderer) {
    myPluginRenderers.add(renderer);
  }

  @Deprecated
  public void removePluginRenderer(NodeRenderer renderer) {
    myPluginRenderers.remove(renderer);
  }
  
  public void setAlternateCollectionViewsEnabled(boolean enabled) {
    for (NodeRenderer myAlternateCollectionRenderer : myAlternateCollectionRenderers) {
      myAlternateCollectionRenderer.setEnabled(enabled);
    }
  }

  public boolean areAlternateCollectionViewsEnabled() {
    return myAlternateCollectionRenderers[0].isEnabled();
  }

  public boolean equals(Object o) {
    if(!(o instanceof NodeRendererSettings)) return false;

    return DebuggerUtilsEx.elementsEqual(getState(), ((NodeRendererSettings)o).getState());
  }

  public void addListener(NodeRendererSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeListener(NodeRendererSettingsListener listener) {
    myDispatcher.removeListener(listener);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public Element getState()  {
    final Element element = new Element("NodeRendererSettings");
    JDOMExternalizerUtil.writeField(element, HEX_VIEW_ENABLED, myHexRenderer.isEnabled()? "true" : "false");
    JDOMExternalizerUtil.writeField(element, ALTERNATIVE_COLLECTION_VIEW_ENABLED, areAlternateCollectionViewsEnabled()? "true" : "false");
    try {
      element.addContent(writeRenderer(myArrayRenderer));
      element.addContent(writeRenderer(myToStringRenderer));
      element.addContent(writeRenderer(myClassRenderer));
      element.addContent(writeRenderer(myPrimitiveRenderer));
      if (myCustomRenderers.getRendererCount() > 0) {
        final Element custom = new Element(CUSTOM_RENDERERS_TAG_NAME);
        element.addContent(custom);
        myCustomRenderers.writeExternal(custom);
      }
    }
    catch (WriteExternalException e) {
      // ignore
    }
    return element;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void loadState(final Element root) {
    final String hexEnabled = JDOMExternalizerUtil.readField(root, HEX_VIEW_ENABLED);
    if (hexEnabled != null) {
      myHexRenderer.setEnabled("true".equalsIgnoreCase(hexEnabled));
    }

    final String alternativeEnabled = JDOMExternalizerUtil.readField(root, ALTERNATIVE_COLLECTION_VIEW_ENABLED);
    if (alternativeEnabled != null) {
      setAlternateCollectionViewsEnabled("true".equalsIgnoreCase(alternativeEnabled));
    }

    final List rendererElements = root.getChildren(RENDERER_TAG);
    for (final Object rendererElement : rendererElements) {
      final Element elem = (Element)rendererElement;
      final String id = elem.getAttributeValue(RENDERER_ID);
      if (id == null) {
        continue;
      }
      try {
        if (ArrayRenderer.UNIQUE_ID.equals(id)) {
          myArrayRenderer.readExternal(elem);
        }
        else if (ToStringRenderer.UNIQUE_ID.equals(id)) {
          myToStringRenderer.readExternal(elem);
        }
        else if (ClassRenderer.UNIQUE_ID.equals(id)) {
          myClassRenderer.readExternal(elem);
        }
        else if (PrimitiveRenderer.UNIQUE_ID.equals(id)) {
          myPrimitiveRenderer.readExternal(elem);
        }
      }
      catch (InvalidDataException e) {
        // ignore
      }
    }
    final Element custom = root.getChild(CUSTOM_RENDERERS_TAG_NAME);
    if (custom != null) {
      myCustomRenderers.readExternal(custom);
    }

    myDispatcher.getMulticaster().renderersChanged();
  }

  public RendererConfiguration getCustomRenderers() {
    return myCustomRenderers;
  }

  public void setCustomRenderers(@NotNull final RendererConfiguration customRenderers) {
    RendererConfiguration oldConfig = myCustomRenderers;
    myCustomRenderers = customRenderers;
    if (oldConfig == null || !oldConfig.equals(customRenderers)) {
      fireRenderersChanged();
    }
  }

  public List<NodeRenderer> getPluginRenderers() {
    return new ArrayList<NodeRenderer>(myPluginRenderers);
  }
  
  public PrimitiveRenderer getPrimitiveRenderer() {
    return myPrimitiveRenderer;
  }

  public ArrayRenderer getArrayRenderer() {
    return myArrayRenderer;
  }

  public ClassRenderer getClassRenderer() {
    return myClassRenderer;
  }

  public HexRenderer getHexRenderer() {
    return myHexRenderer;
  }

  public ToStringRenderer getToStringRenderer() {
    return myToStringRenderer;
  }

  public NodeRenderer[] getAlternateCollectionRenderers() {
    return myAlternateCollectionRenderers;
  }

  public void fireRenderersChanged() {
    myDispatcher.getMulticaster().renderersChanged();
  }

  public List<NodeRenderer> getAllRenderers() {
    // the order is important as the renderers are applied according to it
    final List<NodeRenderer> allRenderers = new ArrayList<NodeRenderer>();
    allRenderers.add(myHexRenderer);
    allRenderers.add(myPrimitiveRenderer);
    allRenderers.addAll(myPluginRenderers);
    Collections.addAll(allRenderers, NodeRenderer.EP_NAME.getExtensions());
    myCustomRenderers.iterateRenderers(new InternalIterator<NodeRenderer>() {
      public boolean visit(final NodeRenderer renderer) {
        allRenderers.add(renderer);
        return true;
      }
    });
    Collections.addAll(allRenderers, myAlternateCollectionRenderers);
    allRenderers.add(myToStringRenderer);
    allRenderers.add(myArrayRenderer);
    allRenderers.add(myClassRenderer);
    return allRenderers;
  }

  public boolean isBase(final Renderer renderer) {
    return renderer == myPrimitiveRenderer || renderer == myArrayRenderer || renderer == myClassRenderer;
  }

  public Renderer readRenderer(Element root) throws InvalidDataException {
    if (root == null) {
      return null;
    }

    if (!RENDERER_TAG.equals(root.getName())) {
      throw new InvalidDataException("Cannot read renderer - tag name is not '" + RENDERER_TAG + "'");
    }

    final String rendererId = root.getAttributeValue(RENDERER_ID);
    if(rendererId == null) {
      throw new InvalidDataException("unknown renderer ID: " + rendererId);
    }

    final Renderer renderer = createRenderer(rendererId);
    if(renderer == null) {
      throw new InvalidDataException("unknown renderer ID: " + rendererId);
    }

    renderer.readExternal(root);

    return renderer;
  }

  public Element writeRenderer(Renderer renderer) throws WriteExternalException {
    Element root = new Element(RENDERER_TAG);
    if(renderer != null) {
      root.setAttribute(RENDERER_ID  , renderer.getUniqueId());
      renderer.writeExternal(root);
    }
    return root;
  }

  public Renderer createRenderer(final String rendererId) {
    if (ClassRenderer.UNIQUE_ID.equals(rendererId)) {
      return myClassRenderer;
    }
    else if (ArrayRenderer.UNIQUE_ID.equals(rendererId)) {
      return myArrayRenderer;
    }
    else if (PrimitiveRenderer.UNIQUE_ID.equals(rendererId)) {
      return myPrimitiveRenderer;
    }
    else if(HexRenderer.UNIQUE_ID.equals(rendererId)) {
      return myHexRenderer;
    }
    else if(rendererId.equals(ExpressionChildrenRenderer.UNIQUE_ID)) {
      return new ExpressionChildrenRenderer();
    }
    else if(rendererId.equals(LabelRenderer.UNIQUE_ID)) {
      return new LabelRenderer();
    }
    else if(rendererId.equals(EnumerationChildrenRenderer.UNIQUE_ID)) {
      return new EnumerationChildrenRenderer();
    }
    else if(rendererId.equals(ToStringRenderer.UNIQUE_ID)) {
      return myToStringRenderer;
    }
    else if(rendererId.equals(CompoundNodeRenderer.UNIQUE_ID) || rendererId.equals(REFERENCE_RENDERER)) {
      return createCompoundReferenceRenderer("unnamed", CommonClassNames.JAVA_LANG_OBJECT, null, null);
    }
    return null;
  }

  private CompoundReferenceRenderer createCompoundReferenceRenderer(
    @NonNls final String rendererName, @NonNls final String className, final ValueLabelRenderer labelRenderer, final ChildrenRenderer childrenRenderer
    ) {
    CompoundReferenceRenderer renderer = new CompoundReferenceRenderer(this, rendererName, labelRenderer, childrenRenderer);
    renderer.setClassName(className);
    return renderer;
  }

  public static ExpressionChildrenRenderer createExpressionChildrenRenderer(@NonNls String expressionText,
                                                                             @NonNls String childrenExpandableText) {
    final ExpressionChildrenRenderer childrenRenderer = new ExpressionChildrenRenderer();
    childrenRenderer.setChildrenExpression(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expressionText, "", StdFileTypes.JAVA));
    if (childrenExpandableText != null) {
      childrenRenderer.setChildrenExpandable(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, childrenExpandableText, "", StdFileTypes.JAVA));
    }
    return childrenRenderer;
  }

  private static EnumerationChildrenRenderer createEnumerationChildrenRenderer(@NonNls String[][] expressions) {
    final EnumerationChildrenRenderer childrenRenderer = new EnumerationChildrenRenderer();
    if (expressions != null && expressions.length > 0) {
      final ArrayList<Pair<String, TextWithImports>> childrenList = new ArrayList<Pair<String, TextWithImports>>(expressions.length);
      for (final String[] expression : expressions) {
        childrenList.add(new Pair<String, TextWithImports>(expression[0], new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expression[1], "", StdFileTypes.JAVA)));
      }
      childrenRenderer.setChildren(childrenList);
    }
    return childrenRenderer;
  }

  private static LabelRenderer createLabelRenderer(@NonNls final String prefix, @NonNls final String expressionText, @NonNls final String postfix) {
    final LabelRenderer labelRenderer = new LabelRenderer() {
      public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener labelListener) throws EvaluateException {
        final String evaluated = super.calcLabel(descriptor, evaluationContext, labelListener);
        if (prefix == null && postfix == null) {
          return evaluated;
        }
        if (prefix != null && postfix != null) {
          return prefix + evaluated + postfix;
        }
        if (prefix != null) {
          return prefix + evaluated;
        }
        return evaluated + postfix;
      }
    };
    labelRenderer.setLabelExpression(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expressionText, "", StdFileTypes.JAVA));
    return labelRenderer;
  }

  private static class MapEntryLabelRenderer extends ReferenceRenderer implements ValueLabelRenderer{
    private static final Computable<String> NULL_LABEL_COMPUTABLE = new Computable<String>() {
      public String compute() {
        return "null";
      }
    };

    private final MyCachedEvaluator myKeyExpression = new MyCachedEvaluator();
    private final MyCachedEvaluator myValueExpression = new MyCachedEvaluator();

    private MapEntryLabelRenderer() {
      super("java.util.Map$Entry");
      myKeyExpression.setReferenceExpression(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, "this.getKey()", "", StdFileTypes.JAVA));
      myValueExpression.setReferenceExpression(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, "this.getValue()", "", StdFileTypes.JAVA));
    }

    public Icon calcValueIcon(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener) throws EvaluateException {
      return null;
    }

    public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener) throws EvaluateException {
      final DescriptorUpdater descriptorUpdater = new DescriptorUpdater(descriptor, listener);

      final Value originalValue = descriptor.getValue();
      final Pair<Computable<String>, ValueDescriptorImpl> keyPair = createValueComputable(evaluationContext, originalValue, myKeyExpression, descriptorUpdater);
      final Pair<Computable<String>, ValueDescriptorImpl> valuePair = createValueComputable(evaluationContext, originalValue, myValueExpression, descriptorUpdater);

      descriptorUpdater.setKeyDescriptor(keyPair.second);
      descriptorUpdater.setValueDescriptor(valuePair.second);

      return DescriptorUpdater.constructLabelText(keyPair.first.compute(), valuePair.first.compute());
    }

    private Pair<Computable<String>, ValueDescriptorImpl> createValueComputable(final EvaluationContext evaluationContext,
                                                                                Value originalValue,
                                                                                final MyCachedEvaluator evaluator,
                                                                                final DescriptorLabelListener listener) throws EvaluateException {
      final Value eval = doEval(evaluationContext, originalValue, evaluator);
      if (eval != null) {
        final WatchItemDescriptor evalDescriptor = new WatchItemDescriptor(evaluationContext.getProject(), evaluator.getReferenceExpression(), eval);
        evalDescriptor.setShowIdLabel(false);
        return new Pair<Computable<String>, ValueDescriptorImpl>(new Computable<String>() {
          public String compute() {
            evalDescriptor.updateRepresentation((EvaluationContextImpl)evaluationContext, listener);
            return evalDescriptor.getValueLabel();
          }
        }, evalDescriptor);
      }
      return new Pair<Computable<String>, ValueDescriptorImpl>(NULL_LABEL_COMPUTABLE, null);
    }

    public String getUniqueId() {
      return "MapEntry renderer";
    }

    private Value doEval(EvaluationContext evaluationContext, Value originalValue, MyCachedEvaluator cachedEvaluator)
      throws EvaluateException {
      final DebugProcess debugProcess = evaluationContext.getDebugProcess();
      if (originalValue == null) {
        return null;
      }
      try {
        final ExpressionEvaluator evaluator = cachedEvaluator.getEvaluator(debugProcess.getProject());
        if(!debugProcess.isAttached()) {
          throw EvaluateExceptionUtil.PROCESS_EXITED;
        }
        final EvaluationContext thisEvaluationContext = evaluationContext.createEvaluationContext(originalValue);
        return evaluator.evaluate(thisEvaluationContext);
      }
      catch (final EvaluateException ex) {
        throw new EvaluateException(DebuggerBundle.message("error.unable.to.evaluate.expression") + " " + ex.getMessage(), ex);
      }
    }

    private class MyCachedEvaluator extends CachedEvaluator {
      protected String getClassName() {
        return MapEntryLabelRenderer.this.getClassName();
      }

      public ExpressionEvaluator getEvaluator(Project project) throws EvaluateException {
        return super.getEvaluator(project);
      }
    }
  }

  private static class ListChildrenRenderer extends ExpressionChildrenRenderer {
    private static final ArrayRenderer ourChildrenRenderer = new ArrayRenderer() {
      @Override
      public PsiExpression getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) {
        try {
          ArrayElementDescriptorImpl descriptor = (ArrayElementDescriptorImpl)node.getDescriptor();
          PsiElementFactory elementFactory = JavaPsiFacade.getInstance(node.getProject()).getElementFactory();
          return elementFactory.createExpressionFromText("get(" + descriptor.getIndex() + ")", null);
        }
        catch (IncorrectOperationException e) {
          // fallback to original
          return super.getChildValueExpression(node, context);
        }
      }
    };

    public ListChildrenRenderer() {
      setChildrenExpression(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, "toArray()", "", StdFileTypes.JAVA));
      setChildrenExpandable(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, "!isEmpty()", "", StdFileTypes.JAVA));
    }

    @Override
    public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {
      if (getLastChildrenRenderer(builder.getParentDescriptor()) == null) {
        setPreferableChildrenRenderer(builder.getParentDescriptor(), ourChildrenRenderer);
      }
      super.buildChildren(value, builder, evaluationContext);
    }
  }

  private static class DescriptorUpdater implements DescriptorLabelListener {
    private final ValueDescriptor myTargetDescriptor;
    @Nullable
    private ValueDescriptorImpl myKeyDescriptor;
    @Nullable
    private ValueDescriptorImpl myValueDescriptor;
    private final DescriptorLabelListener myDelegate;

    private DescriptorUpdater(ValueDescriptor descriptor, DescriptorLabelListener delegate) {
      myTargetDescriptor = descriptor;
      myDelegate = delegate;
    }

    public void setKeyDescriptor(@Nullable ValueDescriptorImpl keyDescriptor) {
      myKeyDescriptor = keyDescriptor;
    }

    public void setValueDescriptor(@Nullable ValueDescriptorImpl valueDescriptor) {
      myValueDescriptor = valueDescriptor;
    }

    public void labelChanged() {
      myTargetDescriptor.setValueLabel(constructLabelText(getDescriptorLabel(myKeyDescriptor), getDescriptorLabel(myValueDescriptor)));
      myDelegate.labelChanged();
    }

    static String constructLabelText(final String keylabel, final String valueLabel) {
      StringBuilder sb = new StringBuilder();
      sb.append('\"').append(keylabel).append("\" -> ");
      if (!StringUtil.isEmpty(valueLabel)) {
        sb.append('\"').append(valueLabel).append('\"');
      }
      return sb.toString();
    }

    private static String getDescriptorLabel(final ValueDescriptorImpl keyDescriptor) {
      return keyDescriptor == null? "null" : keyDescriptor.getValueLabel();
    }
  }
}
