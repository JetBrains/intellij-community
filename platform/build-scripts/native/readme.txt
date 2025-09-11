Scripts for building intellij native tools.

Source code is located at //tools/idea/native
Resulting binaries checked into //tools/idea/bin

Linux tools are built in docker image
 - so that resulting binaries are compatible with older glibc version that users might have on their machines
 - docker image incapsultes custom toolchains required to build linux tools. We don't need to check-in and manage those toolchains in repo

Docker image itself is created by build-docker.sh
   Need gcloud installed and authorized
   sudo apt install -y google-cloud-cli
   gcloud auth application-default login --project="google.com:android-studio-alphasource"

# Local build
   install docker on gLinux foolowing instructions on go/installdocker
   cd tools/idea/platform/build-scripts/native
   ./linux_tools_local_dev.sh