import java.util.stream.Collectors;
import java.util.stream.Stream;

class Main {
  record City(String name, String region, Integer costOfLiving) {
  }

  public static void main(String[] args) {
    Stream<City> stream = Stream.of(new City("Colombo", "South Asia", 987));

    long count = stream.collect(Collectors.filtering(
      o -> ((<warning descr="Casting 'o' to 'City' is redundant">City</warning>) o).region.contains("Asia") && ((<warning descr="Casting 'o' to 'City' is redundant">City</warning>) o).costOfLiving <= 1000,
      Collectors.counting()
    ));

    System.out.println("count = " + count);
  }
}
