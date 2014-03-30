public abstract class PartnerAuthenticationInterceptor {


    protected boolean validateProtection(PartnerAuthentication partnerAuthentication) {
        tryPartnerAuthenticationTypes(validated, ip, partner, "serviceGroup", partnerAuthentication.type());

        return false;
    }


    private boolean tryPartnerAuthenticationTypes(boolean validated, String ip, String partner, String serviceGroup, String type) {
        if (type == PartnerAuthenticationType.IP_PARTNER_ID) {

        } else if (type == PartnerAuthenticationType.IP) {
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
