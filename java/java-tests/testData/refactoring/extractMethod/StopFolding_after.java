class A {
    public static void main(String[] args) {
        StatDescriptor[] allStatMembers = new StatDescriptor[] {};
        WSStats[] allStats = new WSStats[] {};

        StringBuffer sb = new StringBuffer();
        sb.append("All stat members found:\n");
        for (int i = 0; i < allStatMembers.length; i++) {
            StatDescriptor statMember = allStatMembers[i];


            newMethod(allStats[i], sb, statMember);

        }
    }

    private static void newMethod(WSStats allStat, StringBuffer sb, StatDescriptor statMember) {
        sb.append(statMember.toString());

        sb.append(": [");

        WSStatistic[] statistics = allStat.getStatistics();
        for (int j = 0; j < statistics.length; j++) {
            WSStatistic statistic = statistics[j];
            sb.append(statistic.getId()).
                    append('=').
                    append(statistic.getName());
            if (j < statistics.length - 1) {
                sb.append(", ");
            }
        }

        sb.append("]\n");
    }

    private class StatDescriptor {
    }

    private class WSStatistic {
        private Object id;
        private String name;

        public Object getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    private class WSStats {
        private WSStatistic[] statistics;

        public WSStatistic[] getStatistics() {
            return statistics;
        }
    }
}