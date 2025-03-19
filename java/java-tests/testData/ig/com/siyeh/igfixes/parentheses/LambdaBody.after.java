class LambdaBody {
  Comparator<String> comparator = Comparator.comparing(s -> s.substring(2).isEmpty());
}