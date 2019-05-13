import org.jetbrains.annotations.Contract;

import java.util.List;

abstract class Some {
  @Contract("_,false->fail")
  abstract void assertTrue(String s, boolean b);

  void assertContainsAllVariants(List<String> actualVariants, String... expectedVariants) {
    for (String expectedVariant : expectedVariants) {
      assertTrue(expectedVariant, actualVariants.contains(expectedVariant));
    }
  }

}


