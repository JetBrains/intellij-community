package com.intellij.util.net.ssl;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static com.intellij.util.net.ssl.CertificateWrapper.CommonField.COMMON_NAME;
import static com.intellij.util.net.ssl.CertificateWrapper.CommonField.ORGANIZATION;

/**
 * @author Mikhail Golubev
 */
public class CertificateTreeBuilder extends AbstractTreeBuilder {
  private static final SimpleTextAttributes STRIKEOUT_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, null);
  private static final RootDescriptor ROOT_DESCRIPTOR = new RootDescriptor();

  private final MultiMap<String, CertificateWrapper> myCertificates = new MultiMap<>();

  public CertificateTreeBuilder(@NotNull Tree tree) {
    init(tree, new DefaultTreeModel(new DefaultMutableTreeNode()), new MyTreeStructure(), (o1, o2) -> {
      if (o1 instanceof OrganizationDescriptor && o2 instanceof OrganizationDescriptor) {
        return ((String)o1.getElement()).compareTo((String)o2.getElement());
      }
      else if (o1 instanceof CertificateDescriptor && o2 instanceof CertificateDescriptor) {
        String cn1 = ((CertificateDescriptor)o1).getElement().getSubjectField(COMMON_NAME);
        String cn2 = ((CertificateDescriptor)o2).getElement().getSubjectField(COMMON_NAME);
        return cn1.compareTo(cn2);
      }
      return 0;
    }, true);
    initRootNode();
  }

  public void reset(@NotNull Collection<X509Certificate> certificates) {
    myCertificates.clear();
    for (X509Certificate certificate : certificates) {
      addCertificate(certificate);
    }
    // expand organization nodes at the same time
    //initRootNode();
    queueUpdateFrom(RootDescriptor.ROOT, true).doWhenDone(() -> this.expandAll(null));
  }

  public void addCertificate(@NotNull X509Certificate certificate) {
    CertificateWrapper wrapper = new CertificateWrapper(certificate);
    myCertificates.putValue(wrapper.getSubjectField(ORGANIZATION), wrapper);
    queueUpdateFrom(RootDescriptor.ROOT, true);
  }

  /**
   * Remove specified certificate and corresponding organization, if after removal it contains no certificates.
   */
  public void removeCertificate(@NotNull X509Certificate certificate) {
    CertificateWrapper wrapper = new CertificateWrapper(certificate);
    myCertificates.remove(wrapper.getSubjectField(ORGANIZATION), wrapper);
    queueUpdateFrom(RootDescriptor.ROOT, true);
  }

  public List<X509Certificate> getCertificates() {
    return ContainerUtil.map(myCertificates.values(), new Function<CertificateWrapper, X509Certificate>() {
      @Override
      public X509Certificate fun(CertificateWrapper wrapper) {
        return wrapper.getCertificate();
      }
    });
  }

  public boolean isEmpty() {
    return myCertificates.isEmpty();
  }

  public void selectCertificate(@NotNull X509Certificate certificate) {
    select(new CertificateWrapper(certificate));
  }

  public void selectFirstCertificate() {
    if (!isEmpty()) {
      Tree tree = (Tree)getTree();
      TreePath path = TreeUtil.getFirstLeafNodePath(tree);
      tree.addSelectionPath(path);
    }
  }

  /**
   * Returns certificates selected in the tree. If organization node is selected, all its certificates
   * will be returned.
   *
   * @return - selected certificates
   */
  @NotNull
  public Set<X509Certificate> getSelectedCertificates(boolean addFromOrganization) {
    Set<X509Certificate> selected = getSelectedElements(X509Certificate.class);
    if (addFromOrganization) {
      for (String s : getSelectedElements(String.class)) {
        selected.addAll(getCertificatesByOrganization(s));
      }
    }
    return selected;
  }

  @Nullable
  public X509Certificate getFirstSelectedCertificate(boolean addFromOrganization) {
    Set<X509Certificate> certificates = getSelectedCertificates(addFromOrganization);
    return certificates.isEmpty() ? null : certificates.iterator().next();
  }

  @NotNull
  public List<X509Certificate> getCertificatesByOrganization(@NotNull String organizationName) {
    Collection<CertificateWrapper> wrappers = myCertificates.get(organizationName);
    return extract(wrappers);
  }

  private static List<X509Certificate> extract(Collection<CertificateWrapper> wrappers) {
    return ContainerUtil.map(wrappers, wrapper -> wrapper.getCertificate());
  }

  @Override
  protected Object transformElement(Object object) {
    if (object instanceof CertificateWrapper) {
      return ((CertificateWrapper)object).getCertificate();
    }
    return object;
  }

  @Override
  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return super.isAutoExpandNode(nodeDescriptor) || nodeDescriptor instanceof OrganizationDescriptor;
  }

  class MyTreeStructure extends AbstractTreeStructure {
    @Override
    public Object getRootElement() {
      return RootDescriptor.ROOT;
    }

    @Override
    public Object[] getChildElements(Object element) {
      if (element == RootDescriptor.ROOT) {
        return ArrayUtil.toStringArray(myCertificates.keySet());
      }
      else if (element instanceof String) {
        return ArrayUtil.toObjectArray(myCertificates.get((String)element));
      }
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Nullable
    @Override
    public Object getParentElement(Object element) {
      if (element == RootDescriptor.ROOT) {
        return null;
      }
      else if (element instanceof String) {
        return RootDescriptor.ROOT;
      }
      return ((CertificateWrapper)element).getSubjectField(ORGANIZATION);
    }

    @NotNull
    @Override
    public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
      if (element == RootDescriptor.ROOT) {
        return ROOT_DESCRIPTOR;
      }
      else if (element instanceof String) {
        return new OrganizationDescriptor(parentDescriptor, (String)element);
      }
      return new CertificateDescriptor(parentDescriptor, (CertificateWrapper)element);
    }


    @Override
    public void commit() {
      // do nothing
    }

    @Override
    public boolean hasSomethingToCommit() {
      return false;
    }
  }

  // Auxiliary node descriptors

  static abstract class MyNodeDescriptor<T> extends PresentableNodeDescriptor<T> {
    private final T myObject;

    MyNodeDescriptor(@Nullable NodeDescriptor parentDescriptor, @NotNull T object) {
      super(null, parentDescriptor);
      myObject = object;
    }

    @Override
    public T getElement() {
      return myObject;
    }
  }

  static class RootDescriptor extends MyNodeDescriptor<Object> {
    public static final Object ROOT = new Object();

    private RootDescriptor() {
      super(null, ROOT);
    }

    @Override
    protected void update(PresentationData presentation) {
      presentation.addText("<root>", SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }

  static class OrganizationDescriptor extends MyNodeDescriptor<String> {
    private OrganizationDescriptor(@Nullable NodeDescriptor parentDescriptor, @NotNull String object) {
      super(parentDescriptor, object);
    }

    @Override
    protected void update(PresentationData presentation) {
      presentation.addText(getElement(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }
  }

  static class CertificateDescriptor extends MyNodeDescriptor<CertificateWrapper> {
    private CertificateDescriptor(@Nullable NodeDescriptor parentDescriptor, @NotNull CertificateWrapper object) {
      super(parentDescriptor, object);
    }

    @Override
    protected void update(PresentationData presentation) {
      CertificateWrapper wrapper = getElement();
      SimpleTextAttributes attr = wrapper.isValid() ? SimpleTextAttributes.REGULAR_ATTRIBUTES : STRIKEOUT_ATTRIBUTES;
      presentation.addText(wrapper.getSubjectField(COMMON_NAME), attr);
    }
  }
}
