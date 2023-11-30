class X {{

    int[] array;
    int[][] array2;
    int[][][] array3;

    int @Required @Preliminary [] @Preliminary [] arr1, arr2 = new int[0][2], arr3 = new int[0][3]; // same dimensions and annotations

    int @Required @Preliminary [] @Preliminary [] arr4; // same dimensions and different annotations
    int @Preliminary @Required [] @Preliminary [] arr5;
    int @Preliminary [] @Preliminary [] arr6;

    int @Required [] @Preliminary [] arr7; // same dimensions and different annotations
    int @Preliminary @Required [] @Preliminary [] arr8 = new int[0][8];
    int @Preliminary @Required [] @Preliminary [] arr9;

    int[][] arr10 = new int[1][0], arr11 = new int[1][1], arr12 = new int[1][2]; // same dimensions

    int[] @Preliminary [] arr13; // different annotations
    int @Required [] @Preliminary [] arr14;
    int @Required [] @Preliminary [] arr15;
}}