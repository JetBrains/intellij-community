public class Test {
  int a;

  Test() {
    this.field = 0;
  }

  private int getInteger(int num) {
    return num;
  }

  private  int runTest(){
    boolean localVar = true;
    for(int forLoopVariable = 0; forLoopVariable < 0; ++forLoopVariable){
      localVar += 1;

      if (localVar) {
        int nestedIfVar = 0;
      }
    }

    while(localVar){
      boolean whileVariable = true;
      localVar = false;
    }

    if(true){
      int ifVar = 5;
    }
    else{
      int elseVar = 7;
    }

    ArrayList<String> list = new ArrayList();
    for(String forEachVar: list){

    }

    get<caret>Integer(2 + field);
  }
}