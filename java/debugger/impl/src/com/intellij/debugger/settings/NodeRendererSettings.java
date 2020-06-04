// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.settings;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.JavaValuePresentation;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.impl.watch.ArrayElementDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.debugger.ui.tree.ArrayElementDescriptor;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.render.*;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.EventDispatcher;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.Value;
import org.jdom.Element;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

@State(name = "NodeRendererSettings", storages = {
  @Storage("debugger.xml"),
  @Storage(value = "debugger.renderers.xml", deprecated = true),
})
public class NodeRendererSettings implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(NodeRendererSettings.class);

  @NonNls private static final String REFERENCE_RENDERER = "Reference renderer";
  @NonNls public static final String RENDERER_TAG = "Renderer";
  @NonNls private static final String RENDERER_ID = "ID";

  private final EventDispatcher<NodeRendererSettingsListener> myDispatcher = EventDispatcher.create(NodeRendererSettingsListener.class);
  private final RendererConfiguration myCustomRenderers = new RendererConfiguration(this);

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
      createExpressionArrayChildrenRenderer("entrySet().toArray()", "!isEmpty()", myArrayRenderer)
    ),
    createCompoundReferenceRenderer(
      "Map.Entry", "java.util.Map$Entry",
      new MapEntryLabelRenderer()/*createLabelRenderer(null, "\" \" + getKey() + \" -> \" + getValue()", null)*/,
      createEnumerationChildrenRenderer(new String[][]{{"key", "getKey()"}, {"value", "getValue()"}})
    ),
    new ListObjectRenderer(this, myArrayRenderer),
    createCompoundReferenceRenderer(
      "Collection", "java.util.Collection",
      createLabelRenderer(" size = ", "size()", null),
      createExpressionArrayChildrenRenderer("toArray()", "!isEmpty()", myArrayRenderer)
    )
  };
  @NonNls private static final String HEX_VIEW_ENABLED = "HEX_VIEW_ENABLED";
  @NonNls private static final String ALTERNATIVE_COLLECTION_VIEW_ENABLED = "ALTERNATIVE_COLLECTION_VIEW_ENABLED";
  @NonNls private static final String CUSTOM_RENDERERS_TAG_NAME = "CustomRenderers";

  public NodeRendererSettings() {
    // default configuration
    myHexRenderer.setEnabled(false);
    setAlternateCollectionViewsEnabled(true);
  }

  public static NodeRendererSettings getInstance() {
    return ServiceManager.getService(NodeRendererSettings.class);
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

  public void addListener(NodeRendererSettingsListener listener, Disposable disposable) {
    myDispatcher.addListener(listener, disposable);
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public Element getState()  {
    final Element element = new Element("state");
    if (myHexRenderer.isEnabled()) {
      JDOMExternalizerUtil.writeField(element, HEX_VIEW_ENABLED, "true");
    }
    if (!areAlternateCollectionViewsEnabled()) {
      JDOMExternalizerUtil.writeField(element, ALTERNATIVE_COLLECTION_VIEW_ENABLED, "false");
    }

    try {
      addRendererIfNotDefault(myToStringRenderer, element);
      addRendererIfNotDefault(myClassRenderer, element);
      addRendererIfNotDefault(myPrimitiveRenderer, element);
      if (myCustomRenderers.getRendererCount() > 0) {
        final Element custom = new Element(CUSTOM_RENDERERS_TAG_NAME);
        element.addContent(custom);
        myCustomRenderers.writeExternal(custom);
      }
    }
    catch (WriteExternalException ignore) {
    }
    return element;
  }

  private void addRendererIfNotDefault(@NotNull Renderer renderer, @NotNull Element to) {
    Element element = writeRenderer(renderer);
    if (element.getContentSize() == 0 && element.getAttributes().size() <= 1 /* ID attribute */) {
      return;
    }

    to.addContent(element);
  }

  @Override
  public void loadState(@NotNull final Element root) {
    final String hexEnabled = JDOMExternalizerUtil.readField(root, HEX_VIEW_ENABLED);
    if (hexEnabled != null) {
      myHexRenderer.setEnabled(Boolean.parseBoolean(hexEnabled));
    }

    final String alternativeEnabled = JDOMExternalizerUtil.readField(root, ALTERNATIVE_COLLECTION_VIEW_ENABLED);
    if (alternativeEnabled != null) {
      setAlternateCollectionViewsEnabled(Boolean.parseBoolean(alternativeEnabled));
    }

    for (final Element elem : root.getChildren(RENDERER_TAG)) {
      final String id = elem.getAttributeValue(RENDERER_ID);
      if (id == null) {
        continue;
      }
      try {
        if (ToStringRenderer.UNIQUE_ID.equals(id)) {
          myToStringRenderer.readExternal(elem);
          if (!myToStringRenderer.isEnabled()) {
            myToStringRenderer.setEnabled(true);
            myToStringRenderer.setOnDemand(true);
          }
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

  public List<NodeRenderer> getAllRenderers(Project project) {
    // the order is important as the renderers are applied according to it
    final List<NodeRenderer> allRenderers = new ArrayList<>();

    // user defined renderers must come first
    myCustomRenderers.iterateRenderers(renderer -> {
      allRenderers.add(renderer);
      return true;
    });

    if (Registry.is("debugger.renderers.annotations")) {
      ReadAction.run(() -> addAnnotationRenderers(allRenderers, project));
    }

    // plugins registered renderers come after that
    CompoundRendererProvider.EP_NAME.extensions().map(CompoundRendererProvider::createRenderer).forEach(allRenderers::add);
    allRenderers.addAll(NodeRenderer.EP_NAME.getExtensionList());

    // now all predefined stuff
    allRenderers.add(myHexRenderer);
    allRenderers.add(myPrimitiveRenderer);
    Collections.addAll(allRenderers, myAlternateCollectionRenderers);
    allRenderers.add(myToStringRenderer);
    allRenderers.add(myArrayRenderer);
    allRenderers.add(myClassRenderer);
    return allRenderers;
  }

  private void addAnnotationRenderers(List<NodeRenderer> renderers, Project project) {
    try {
      visitAnnotatedElements(Debug.Renderer.class.getName().replace("$", "."), project, (e, annotation) -> {
        if (e instanceof PsiClass) {
          String text = getAttributeValue(annotation, "text");
          LabelRenderer labelRenderer = StringUtil.isEmpty(text) ? null : createLabelRenderer(null, text, null);
          String childrenArray = getAttributeValue(annotation, "childrenArray");
          String isLeaf = getAttributeValue(annotation, "hasChildren");
          ExpressionChildrenRenderer childrenRenderer =
            StringUtil.isEmpty(childrenArray) ? null : createExpressionArrayChildrenRenderer(childrenArray, isLeaf, myArrayRenderer);
          PsiClass cls = ((PsiClass)e);
          CompoundReferenceRenderer renderer = createCompoundReferenceRenderer(
            cls.getQualifiedName(), cls.getQualifiedName(), labelRenderer, childrenRenderer);
          renderer.setEnabled(true);
          renderers.add(renderer);
        }
      });
    }
    catch (IndexNotReadyException | ProcessCanceledException ignore) {
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private static String getAttributeValue(PsiAnnotation annotation, String attribute) {
    PsiAnnotationMemberValue value = annotation.findAttributeValue(attribute);
    if (value == null) {
      return null;
    }
    if (value instanceof PsiLiteralValue) {
      return String.valueOf(((PsiLiteralValue)value).getValue());
    }
    throw new IllegalStateException("String literal expected, but was " + value);
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

  @NotNull
  public Element writeRenderer(Renderer renderer) throws WriteExternalException {
    Element root = new Element(RENDERER_TAG);
    if (renderer != null) {
      root.setAttribute(RENDERER_ID, renderer.getUniqueId());
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
    else if (rendererId.equals(ToStringRenderer.UNIQUE_ID)) {
      return myToStringRenderer;
    }
    else if (rendererId.equals(CompoundReferenceRenderer.UNIQUE_ID) ||
             rendererId.equals(CompoundReferenceRenderer.UNIQUE_ID_OLD) ||
             rendererId.equals(REFERENCE_RENDERER)) {
      return createCompoundReferenceRenderer("unnamed", CommonClassNames.JAVA_LANG_OBJECT, null, null);
    }
    return null;
  }

  public CompoundReferenceRenderer createCompoundReferenceRenderer(
    @NonNls final String rendererName, @NonNls final String className, final ValueLabelRenderer labelRenderer, final ChildrenRenderer childrenRenderer
    ) {
    CompoundReferenceRenderer renderer = new CompoundReferenceRenderer(this, rendererName, labelRenderer, childrenRenderer);
    renderer.setClassName(className);
    renderer.setIsApplicableChecker(type -> DebuggerUtilsAsync.instanceOf(type, renderer.getClassName()));
    return renderer;
  }

  private static ExpressionChildrenRenderer createExpressionArrayChildrenRenderer(String expressionText,
                                                                                  String childrenExpandableText,
                                                                                  ArrayRenderer arrayRenderer) {
    ExpressionChildrenRenderer renderer = createExpressionChildrenRenderer(expressionText, childrenExpandableText);
    renderer.setPredictedRenderer(arrayRenderer);
    return renderer;
  }

  public static ExpressionChildrenRenderer createExpressionChildrenRenderer(@NonNls String expressionText,
                                                                             @NonNls String childrenExpandableText) {
    final ExpressionChildrenRenderer childrenRenderer = new ExpressionChildrenRenderer();
    childrenRenderer.setChildrenExpression(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expressionText, "", JavaFileType.INSTANCE));
    if (childrenExpandableText != null) {
      childrenRenderer.setChildrenExpandable(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, childrenExpandableText, "", JavaFileType.INSTANCE));
    }
    return childrenRenderer;
  }

  public static EnumerationChildrenRenderer createEnumerationChildrenRenderer(@NonNls String[][] expressions) {
    EnumerationChildrenRenderer childrenRenderer = new EnumerationChildrenRenderer();
    if (expressions != null && expressions.length > 0) {
      ArrayList<EnumerationChildrenRenderer.ChildInfo> childrenList = new ArrayList<>(expressions.length);
      for (String[] expression : expressions) {
        childrenList.add(new EnumerationChildrenRenderer.ChildInfo(
          expression[0], new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expression[1], "", JavaFileType.INSTANCE), false));
      }
      childrenRenderer.setChildren(childrenList);
    }
    return childrenRenderer;
  }

  private static LabelRenderer createLabelRenderer(@NonNls final String prefix, @NonNls final String expressionText, @NonNls final String postfix) {
    final LabelRenderer labelRenderer = new LabelRenderer() {
      @Override
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
    labelRenderer.setLabelExpression(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expressionText, "", JavaFileType.INSTANCE));
    return labelRenderer;
  }

  private static class MapEntryLabelRenderer extends ReferenceRenderer
    implements ValueLabelRenderer, XValuePresentationProvider, OnDemandRenderer {
    private static final Key<ValueDescriptorImpl> KEY_DESCRIPTOR = Key.create("KEY_DESCRIPTOR");
    private static final Key<ValueDescriptorImpl> VALUE_DESCRIPTOR = Key.create("VALUE_DESCRIPTOR");

    private final MyCachedEvaluator myKeyExpression = new MyCachedEvaluator();
    private final MyCachedEvaluator myValueExpression = new MyCachedEvaluator();

    private MapEntryLabelRenderer() {
      super("java.util.Map$Entry");
      myKeyExpression.setReferenceExpression(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, "this.getKey()", "", JavaFileType.INSTANCE));
      myValueExpression.setReferenceExpression(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, "this.getValue()", "", JavaFileType.INSTANCE));
    }

    @Override
    public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener) throws EvaluateException {
      if (!isShowValue(descriptor, evaluationContext)) {
        return "";
      }
      String keyText = calcExpression(evaluationContext, descriptor, myKeyExpression, listener, KEY_DESCRIPTOR);
      String valueText = calcExpression(evaluationContext, descriptor, myValueExpression, listener, VALUE_DESCRIPTOR);
      return keyText + " -> " + valueText;
    }

    private String calcExpression(EvaluationContext evaluationContext,
                                  ValueDescriptor descriptor,
                                  MyCachedEvaluator evaluator,
                                  DescriptorLabelListener listener,
                                  Key<ValueDescriptorImpl> key) throws EvaluateException {
      Value eval = doEval(evaluationContext, descriptor.getValue(), evaluator);
      if (eval != null) {
        WatchItemDescriptor evalDescriptor = new WatchItemDescriptor(
          evaluationContext.getProject(), evaluator.getReferenceExpression(), eval, (EvaluationContextImpl)evaluationContext) {
          @Override
          public void updateRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener) {
            updateRepresentationNoNotify(context, labelListener);
          }
        };
        evalDescriptor.updateRepresentation((EvaluationContextImpl)evaluationContext, listener);
        descriptor.putUserData(key, evalDescriptor);
        return evalDescriptor.getValueLabel();
      }
      descriptor.putUserData(key, null);
      return "null";
    }

    @Override
    public String getUniqueId() {
      return "MapEntry renderer";
    }

    @NotNull
    @Override
    public String getLinkText() {
      return JavaDebuggerBundle.message("message.node.evaluate");
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
        throw new EvaluateException(JavaDebuggerBundle.message("error.unable.to.evaluate.expression") + " " + ex.getMessage(), ex);
      }
    }

    private class MyCachedEvaluator extends CachedEvaluator {
      @Override
      protected String getClassName() {
        return MapEntryLabelRenderer.this.getClassName();
      }

      @Override
      public ExpressionEvaluator getEvaluator(Project project) throws EvaluateException {
        return super.getEvaluator(project);
      }
    }

    @NotNull
    @Override
    public XValuePresentation getPresentation(ValueDescriptorImpl descriptor) {
      boolean inCollection = descriptor instanceof ArrayElementDescriptor;
      return new JavaValuePresentation(descriptor) {
        @Override
        public void renderValue(@NotNull XValueTextRenderer renderer, @Nullable XValueNodeImpl node) {
          renderDescriptor(KEY_DESCRIPTOR, renderer, node);
          renderer.renderComment(" -> ");
          renderDescriptor(VALUE_DESCRIPTOR, renderer, node);
        }

        private void renderDescriptor(Key<ValueDescriptorImpl> key, @NotNull XValueTextRenderer renderer, @Nullable XValueNodeImpl node) {
          ValueDescriptorImpl valueDescriptor = myValueDescriptor.getUserData(key);
          if (valueDescriptor != null) {
            String type = valueDescriptor.getIdLabel();
            if (inCollection && type != null) {
              renderer.renderComment("{" + type + "} ");
            }
            new JavaValuePresentation(valueDescriptor).renderValue(renderer, node);
          }
          else {
            renderer.renderValue("null");
          }
        }

        @NotNull
        @Override
        public String getSeparator() {
          return inCollection ? "" : super.getSeparator();
        }

        @Override
        public boolean isShowName() {
          return !inCollection;
        }

        @Nullable
        @Override
        public String getType() {
          return inCollection ? null : super.getType();
        }
      };
    }
  }

  private static class ListObjectRenderer extends CompoundReferenceRenderer {
    ListObjectRenderer(NodeRendererSettings rendererSettings, ArrayRenderer arrayRenderer) {
      super(rendererSettings,
            "List",
            createLabelRenderer(" size = ", "size()", null),
            createExpressionArrayChildrenRenderer("toArray()", "!isEmpty()", arrayRenderer));
      setClassName(CommonClassNames.JAVA_UTIL_LIST);
      setIsApplicableChecker(type -> DebuggerUtilsAsync.instanceOf(type, getClassName()));
    }

    @Override
    public PsiElement getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
      LOG.assertTrue(node.getDescriptor() instanceof ArrayElementDescriptorImpl);
      try {
        return getChildValueExpression("this.get(" + ((ArrayElementDescriptorImpl)node.getDescriptor()).getIndex() + ")", node, context);
      }
      catch (IncorrectOperationException e) {
        // fallback to original
        return super.getChildValueExpression(node, context);
      }
    }
  }

  static void visitAnnotatedElements(String annotationFqn, Project project, BiConsumer<PsiModifierListOwner, PsiAnnotation> consumer) {
    JavaAnnotationIndex.getInstance().get(StringUtil.getShortName(annotationFqn), project, GlobalSearchScope.allScope(project))
      .forEach(annotation -> {
        PsiElement parent = annotation.getContext();
        if (annotationFqn.equals(annotation.getQualifiedName()) && parent instanceof PsiModifierList) {
          PsiElement owner = parent.getParent();
          if (owner instanceof PsiModifierListOwner) {
            consumer.accept((PsiModifierListOwner)owner, annotation);
          }
        }
      });
  }
}
