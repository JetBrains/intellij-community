import java.util.List;
import java.util.function.Predicate;

final class Mapping { }

class Main {

   public <A extends Mapping> List<A> n(Predicate<? super A> filter) {
      return m(filter);
   }

   private <B extends Mapping> List<B> m( Predicate<? super B> filter) {
      return null;
   }

}
