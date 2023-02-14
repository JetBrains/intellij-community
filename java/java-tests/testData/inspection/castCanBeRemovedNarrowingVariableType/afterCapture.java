// "Change type of 'obj' to 'Reference<?>' and remove cast" "true"
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;

class CastToRef {
  private static final ReferenceQueue<Object> queue = new ReferenceQueue<>();

  public static void main(String[] args) throws InterruptedException {
    Reference<?> obj = queue.remove();
    obj.clear();
  }
}