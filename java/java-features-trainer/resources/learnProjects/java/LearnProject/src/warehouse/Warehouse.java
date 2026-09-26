package warehouse;

import java.util.HashMap;
import java.util.Map;

import util.Utils;

public final class Warehouse {
    // Fruit name to amount of it in warehouse
    private final Map<String, Integer> entry = new HashMap<>(); // Apple, banana, etc...

    public Warehouse() {
        String[] availableFruits = Utils.FRUITS;
        for (String fruit : availableFruits) {
            entry.put(fruit, 0);
        }
    }

  /**
   * @param fruitName some fruit name from Utils.FRUITS (mango, apple...)
   */
    public void addFruits(String fruitName, int quantity) {
        Integer curQuantity = entry.get(fruitName);
        if (curQuantity != null) {
            entry.put(fruitName, curQuantity + quantity);
        }
        else {
            throw new IllegalArgumentException("Not found fruit with name: " + fruitName);
        }
    }

    public boolean takeFruit(String fruitName) {
        Integer curQuantity = entry.get(fruitName);
        if (curQuantity == null) {
            throw new IllegalArgumentException("Not found fruit with name: " + fruitName);
        }
        else if (curQuantity > 0) {
            entry.put(fruitName, curQuantity - 1);
            return true;
        }
        return false;
    }

    public void printAllFruits() {
        for (Map.Entry<String, Integer> pair : entry.entrySet()) {
            System.out.println(pair.getKey() + ": " + pair.getValue());
        }
    }
}