import java.util.*;

class Main {
  public static void main(String[] args) {
    Integer[] arr = new Integer[]{568, 659, 685, 235, 258, 657, 159, 357, 756, 958, 657};

    Arrays.sort(arr, Collections.reverseOrder());

    int index1 = Arrays.binarySearch(arr, 756, Comparator.reverseOrder());
    int index2 = Arrays.binarySearch(arr, 657, Comparator.reverseOrder());

    Integer[] newArr = Arrays.copyOf(arr, 15);
    Arrays.fill(newArr, arr.length, newArr.length, 999);

    System.out.println(Arrays.toString(arr));
    System.out.println(index1);
    System.out.println(index2);
    System.out.println(Arrays.toString(newArr));
  }
}

