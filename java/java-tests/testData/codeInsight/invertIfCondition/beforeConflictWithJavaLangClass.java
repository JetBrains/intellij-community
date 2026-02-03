// "Invert 'if' condition" "true"
class Main2 {
  private static class Process {    }

  public static void main(String[] args) {
    List<Process> processList = null;
    i<caret>f (processList == null) {
      return;
    }
    for (Process process : processList) {
      System.out.println("process = " + process);
    }
  }
}