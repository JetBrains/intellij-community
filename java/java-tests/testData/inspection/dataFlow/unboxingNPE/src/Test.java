class Auto {
    public Auto(int k) {
    }

    int f(int k, Auto other) {
        {
            Integer i = null;
            if (i>0) {
            }
        }

        {
            Integer i = null;
            int i1 = (int) i;
        }
        {
            Integer i = null;
            int i1 = i+i;
        }
        {
            Integer i = null;
            int i1 = i++;
        }
        {
            Integer i = null;
            Integer i1 = i++;
        }
        {
            Integer i = null;
            int[] ia = new int[0];
            int i2 = ia[i];
        }
        {
            Integer i = null;
            int[] i2 = {i};
        }
        {
            Boolean i = null;
            boolean i2 = this==other;
            i2 &= i;
        }
        {
            Boolean i = null;
            boolean i2 = this==other;
            i2 |= i;
        }
        {
            Boolean i = null;
            boolean i2 = this==other;
            i2 = !i;
        }

        {
            Integer i = null;
            if (this==other) {
                return i;
            }
        }
        {
            Integer i = null;
            switch(i) {
                case 0:
            }
        }
        {
            Boolean i = null;
            boolean i2 = i && i;
        }
        {
            Boolean i = null;
            boolean i2 = i | i;
        }
        {
            Boolean i = null;
            boolean i2 = true ^ i;
        }
        {
            Boolean i = null;
            boolean i2 = i ? true : false;
        }
        {
            Integer i = null;
            f(i);
        }
        {
            Integer i = null;
            new Auto(i);
        }
        {
            Integer i = null;
            i++;
        }
        {
            Integer i = null;
            --i;
        }
        {
            Boolean i = null;
            Boolean i2 = !i;
        }

        return 0;
    }
}
