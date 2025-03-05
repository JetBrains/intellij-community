class Comments {
  String foo(int par) {
    i<caret>f(par == 11)
      return "ciao";/* case 1*/
    else if(par == 14)
      return "fourteen";//case 2
    else if(par == 15)
      return "fifteen"; //case 3
    else if(par == 16)
      //case 4
      return "sixteen";


    return "default";
  }
}