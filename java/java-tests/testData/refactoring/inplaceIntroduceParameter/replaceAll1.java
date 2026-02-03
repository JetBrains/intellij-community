public abstract class PartnerAuthenticationInterceptor {


    protected boolean validateProtection(PartnerAuthentication partnerAuthentication) {
        tryPartnerAuthenticationTypes(partnerAuthentication, validated, ip, partner, "serviceGroup");

        return false;
    }


    private boolean tryPartnerAuthenticationTypes(PartnerAuthentication partnerAuthentication, boolean validated, String ip, String partner, String serviceGroup) {
        if (partnerAuthentication.typ<caret>e() == PartnerAuthenticationType.IP_PARTNER_ID) {

        } else if (partnerAuthentication.type() == PartnerAuthenticationType.IP) {
        }
        return validated;
    }


    private class PartnerAuthentication {
        public String serviceGroup() {
            return null;  
        }

        public String type() {
            return null;
        }
    }

    private static class PartnerIdHolder {
        private static PartnerIP partnerIP;

        public static PartnerIP getPartnerIP() {
            return partnerIP;
        }
    }

    private class PartnerIP {
        private String ip;
        private String partner;
        private boolean valid;

        public String getIp() {
            return ip;
        }

        public String getPartner() {
            return partner;
        }

        public boolean isValid() {
            return valid;
        }
    }

    private class PartnerAuthenticationType {
        public static final String IP_PARTNER_ID = "id";
    }
}
