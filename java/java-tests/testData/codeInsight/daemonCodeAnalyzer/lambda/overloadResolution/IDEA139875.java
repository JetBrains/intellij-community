import java.util.List;

class GenericsError {
  private static class ListHolder<T> {
    private List<T> <warning descr="Private field 'list' is never assigned">list</warning>;

    private void <warning descr="Private method 'forEach(GenericsError.Looper<? super T>)' is never used">forEach</warning>(final Looper<? super T> looper) {
      for (final T item : list) {
        looper.loopItem(item);
      }
    }


    private void forEach(final DeletingLooper<? super T> looper) {
      for (final T item : list) {
        looper.loopItem(item);
      }
    }
  }

  private interface Looper<T> {
    void loopItem(final T a);
  }

  private interface DeletingLooper<T> extends Looper<T> {
  }

  private static class MyDeletingLooper implements DeletingLooper<B> {
    @Override
    public void loopItem(final B a) {
    }
  }

  private class A {
  }

  private class B extends A {
  }


  public static void main(final String[] args) {
    final ListHolder<? extends B> aListHolder = new ListHolder<B>();
    final DeletingLooper<? super B> bLooper = new MyDeletingLooper();

    aListHolder.forEach(bLooper);
  }
}