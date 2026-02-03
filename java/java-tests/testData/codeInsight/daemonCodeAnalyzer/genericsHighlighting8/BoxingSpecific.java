import java.util.*;

abstract class A {
    void computeCostIfNeeded(Map<Object, Integer> costMap) {
        Math.min(costMap.get(null), 1);
    }
}
