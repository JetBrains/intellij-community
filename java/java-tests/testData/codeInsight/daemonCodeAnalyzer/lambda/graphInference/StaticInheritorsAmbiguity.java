
import java.util.Collection;
import java.util.HashSet;


class A {
  Collection<String> thrownInTryStatement = null;
  final Collection<String> thrownTypes = ContainerUtil.newHashSet( thrownInTryStatement);
}


class ContainerUtil extends ContainerUtilRt{
  public static <T> HashSet<T> newHashSet(Iterable<? extends T> iterable) {
    return null;
  }
}


class ContainerUtilRt {
  public static <T> HashSet<T> newHashSet(Iterable<? extends T> iterable) {
    return null;
  }

}
