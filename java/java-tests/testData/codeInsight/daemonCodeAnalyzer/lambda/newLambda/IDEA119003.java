package problems;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.*;

import static java.util.stream.Collectors.*;

class Test {

  enum CaloricLevel { DIET, NORMAL, FAT }

  public static void main(String[] args) {
    List<Dish> menu = Arrays.asList(
      new Dish("pork", false, 800, Dish.Type.MEAT),
      new Dish("beef", false, 700, Dish.Type.MEAT),
      new Dish("chicken", false, 400, Dish.Type.MEAT),
      new Dish("french fries", true, 530, Dish.Type.OTHER),
      new Dish("rice", true, 350, Dish.Type.OTHER),
      new Dish("season fruit", true, 120, Dish.Type.OTHER),
      new Dish("pizza", true, 550, Dish.Type.OTHER),
      new Dish("prawns", false, 400, Dish.Type.FISH),
      new Dish("salmon", false, 450, Dish.Type.FISH)
    );

    System.out.println(
      menu.stream().collect(reducing(0, Dish::getCalories, (Integer i, Integer j) -> i + j))
    );

    System.out.println(
      menu.stream().collect(
        groupingBy(Dish::getType, mapping(
          dish -> { if (dish.getCalories() <= 400) return CaloricLevel.DIET;
          else if (dish.getCalories() <= 700) return CaloricLevel.NORMAL;
          else return CaloricLevel.FAT; },
          toSet())))
    );

    System.out.println(
      menu.stream().collect(
        groupingBy(Dish::getType,
                   collectingAndThen(
                     reducing((d1, d2) -> d1.getCalories() > d2.getCalories() ? d1 : d2),
                     Optional::get)))
    );
  }
}

class Dish {
  private final String name;
  private final boolean vegetarian;
  private final int calories;
  private final Type type;

  public Dish(String name, boolean vegetarian, int calories, Type type) {
    this.name = name;
    this.vegetarian = vegetarian;
    this.calories = calories;
    this.type = type;
  }

  public String getName() {
    return name;
  }

  public boolean isVegetarian() {
    return vegetarian;
  }

  public int getCalories() {
    return calories;
  }

  public Type getType() {
    return type;
  }

  public enum Type { MEAT, FISH, OTHER }

  @Override
  public String toString() {
    return name;
  }
}

class Test67 {

  static {
    collectingAndThen(reducing(), Optional::get);
    collectingAndThen(reducing(), o -> o.get());
  }

  static <T> List<Optional<T>> reducing() {
    return null;
  }

  static <R,RR> void collectingAndThen(List<R> downstream, Function<R,RR> finisher){}
}

class Test99 {

  {
    collectingAndThen(reducing((d1, d2) ->   d2),  Optional::get);
  }

  public static <COL, R, RR> void collectingAndThen(Collector<COL, R> downstream, Function<R, RR> finisher) {}

  public static <RED> Collector<RED, Optional<RED>> reducing(BinaryOperator<RED> op) {
    return null;
  }

  static class Collector<A, C>{}
}
