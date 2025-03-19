class Compared {
  private int compareDutyId(String[] dutyId1, String[] dutyId2, int idPartIdx) {
      while (true) {
          //1
          int compareResult = Integer.valueOf(dutyId1[idPartIdx]).compareTo(Integer.valueOf(dutyId2[idPartIdx]));
          //2
          if (compareResult == 0) {
              //2
              idPartIdx++;
              //3
              if (idPartIdx == dutyId1.length && idPartIdx == dutyId2.length) {
                  //4
                  return 0;
              } else if (idPartIdx == dutyId1.length) {
                  //5
                  return -1;
                  //6
              } else if (idPartIdx == dutyId2.length) {
                  //7
                  return 1;
                  //8
              }
              // 9
              //10
              //11
              //12
              //13
          } else {
              //14
              return compareResult;
          }
          //15
      }
  }
}