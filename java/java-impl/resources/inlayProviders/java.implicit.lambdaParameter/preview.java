void hintsDemo() {
  List<Integer> list = Arrays.asList(1, 2, 3);
  List<Integer> filteredList = list.stream()
    .filter(/*<# List<Integer> #>*/el -> el >= 3)
    .toList();
}