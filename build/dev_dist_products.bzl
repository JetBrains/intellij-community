"""The per-product facts a split dev distribution's platform set needs, for the community products.

A product is keyed by its `community/build/dev-build.json` key, the same key `DEV_DIST_PLANS` uses. One entry per
product whose dev launchers run from a split dev distribution. Data only: this file loads nothing, so a unit test can
read it without analysis. `build/dev_dist_products.bzl` of the ultimate checkout is the twin of this file.
"""

def _ide(platform_set_prefix, images, plugin_xml, application_info, extra_project_files = []):
    """The entry of one IDE product.

    `images` is the filegroup of the product's `imagesDirectoryPath`, `plugin_xml` its root descriptor, and
    `application_info` the `ApplicationInfo` of its `applicationInfoModule`. `findApplicationInfoInSources` reads the
    two files from the checkout. `extra_project_files` names the other checkout files the product's `ProductProperties`
    read.
    """
    return struct(
        platform_set = platform_set_prefix + "_dev_platform",
        extra_project_files = [images, plugin_xml, application_info] + extra_project_files,
    )

DEV_DIST_PRODUCTS = {
    # `AndroidStudioProperties` extends the community IDEA properties, so its images are the community ones.
    "AndroidStudio": _ide(
        "android_studio",
        "//build:idea_community_images",
        "//android-customization:resources/META-INF/AndroidStudioPlugin.xml",
        "//android-customization:resources/idea/AndroidStudioApplicationInfo.xml",
    ),
    # `IdeaCommunityProperties`, the IDEA Community product.
    "Idea": _ide(
        "idea_community",
        "//build:idea_community_images",
        "//community-resources:resources/META-INF/IdeaPlugin.xml",
        "//community-resources:resources/idea/IdeaApplicationInfo.xml",
    ),
}

def dev_dist_product(product):
    """Returns the `DEV_DIST_PRODUCTS` entry of one product, or fails with the product name and this file."""
    entry = DEV_DIST_PRODUCTS.get(product)
    if entry == None:
        fail("Product '%s' has no entry in DEV_DIST_PRODUCTS (community/build/dev_dist_products.bzl)" % product)
    return entry
