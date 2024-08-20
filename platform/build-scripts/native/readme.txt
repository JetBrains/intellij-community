Scripts for building intellij native tools.

Source code is located at //tools/idea/native
Resulting binaries checked into //tools/idea/bin

Linux tools are built in docker image
 - so that resulting binaries are compatible with older glibc version that users might have on their machines
 - docker image incapsultes custom toolchains required to build linux tools. We don't need to check-in and manage those toolchains in repo
Docker image itself is created by build-docker.sh

# Debugging failures inside docker
   cd tools/idea/platform/build-scripts/native
   sudo docker run -it -v $(realpath ../../../)/native:/home/builder/tools/idea/native -v $(realpath .):/home/builder/tools/idea/platform/build-scripts/native $(sudo docker build -q .)

from docker shell:
   ./tools/idea/platform/build-scripts/native/linux_tools.sh out dist 111