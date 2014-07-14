import java.util.*;
import java.util.function.Predicate;

abstract class Test {
  public long countTweetsLongerThan(int numberOfChars, final List<String> tweetList) {
    //numberOfChars = 100; //TODO uncomment to show it must be effectively final

    long totalByFor = 0;
    for (String tweet : tweetList) {
      if (tweet.length() > numberOfChars) {
        totalByFor++;
      }
    }

    final ArrayList<String> guavaList = newArrayList(fil<ref>ter(tweetList, new Predicate<String>() {
      @Override
      public boolean test(String tweet) {
        return false;
      }
    }));
    final long totalFromGuava = guavaList.size();


    final long totalFromLambda = tweetList.stream()
      .filter(t -> t.length() > numberOfChars)
      .count();

    if (totalByFor != totalFromLambda | totalByFor != totalFromGuava) {
      throw new RuntimeException("");
    }
    return totalFromLambda;
  }

  abstract <E> ArrayList<E> newArrayList(Iterable<? extends E> elements);
  abstract <E> ArrayList<E> newArrayList();
  abstract <E> ArrayList<E> newArrayList(E... elements);

  abstract <T> Iterable<T> filter(Iterable<T> unfiltered, Predicate<? super T> predicate);
  abstract <T> Iterable<T> filter(Iterable<?> unfiltered, Class<T> type);

}
