// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(int a[]) {
        int n = 0;
        for (int i=0; i<a.length; i++) {
            if (n < a[i]) n = a[i];
            if (n > 100) {
                // at the end 1
                return /* return 1 */ n /* return 2 */;
            }
            if (n < 0) {
                // at the end 2
                return /* return 1 */ 0 /* return 2 */;
                /* inline */
            }
        }
        return /* return 1 */ n /* return 2 */;
    }
}