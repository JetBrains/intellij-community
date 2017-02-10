IntelliJ Platform Credentials Store API allows you to securely store sensitive user data.

Previously known as `Password Safe`, it is still named so for users, but internally and for developers was renamed as`Credentials Store` since IntelliJ Platform 2016.3.

# Where Data is Stored

MacOS: Keychain (using [Security Framework](https://developer.apple.com/library/mac/documentation/Security/Reference/keychainservices/)).

Linux: [Secret Service API](https://standards.freedesktop.org/secret-service/) (using [libsecret](https://wiki.gnome.org/Projects/Libsecret)).

Windows: file in the [KeePass](http://keepass.info) format (key encrypted using [Crypt32 API](https://msdn.microsoft.com/en-us/library/windows/desktop/aa380261(v=vs.85).aspx)).

# Glossary

## Service name

The combined name of your service and name of service that requires authentication.
 
* In the reverse-DNS format: `com.apple.facetime: registrationV1`.
* Or in the prefixed human-readable format: `IntelliJ Platform Settings Repository — github.com`, where `IntelliJ Platform` is a required prefix. You must always use this prefix.

## Credentials

Pair of user and password. `user` may be an account name (e.g. `john`) or a path to SSH key file (e.g. `/Users/john/.ssh/id_rsa`).

## Credential Attributes

Attributes of credentials — service name and user name. Can be another attributes, depends on implementation (e.g. requestor). Only platform can define and use additional attributes.

# How to Store and Retrieve Credentials

You should store user as user and password as password. Not service name as user and joined user&password as password – as it was before IntelliJ Platform 2016.3.

API allows you to get credentials only by service name.

See `CredentialStore`. API has two methods:

* `get`: Pass service name and user name. Or only service name — in this case first matched entry will be returned.
* `set`: Pass credentials attributes and credentials.